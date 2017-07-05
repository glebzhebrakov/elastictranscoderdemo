package com.glebzhebrakov.elastictranscoderdemo.service;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoder;
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient;
import com.amazonaws.services.elastictranscoder.model.CreateJobOutput;
import com.amazonaws.services.elastictranscoder.model.CreateJobRequest;
import com.amazonaws.services.elastictranscoder.model.CreateJobResult;
import com.amazonaws.services.elastictranscoder.model.JobInput;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.glebzhebrakov.elastictranscoderdemo.model.JobStatusNotification;
import com.glebzhebrakov.elastictranscoderdemo.model.JobStatusNotificationHandler;
import com.glebzhebrakov.elastictranscoderdemo.transcode.utils.SqsQueueNotificationWorker;
import com.glebzhebrakov.elastictranscoderdemo.transcode.utils.TranscoderSampleUtilities;
import com.google.common.io.Files;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AudioConversionService {

    // This is the URL of the SQS queue that was created when setting up your
    // AWS environment.
    // http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/sample-code.html#java-sqs
    private static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/893574907024/conversionqueue.fifo";

    // This is the name of the input key that you would like to transcode.

    // This will generate a 480p 16:9 mp4 output.
    private static final String PRESET_ID = "1499175956623-s1cf70";
//    private static final String PRESET_ID = "1351620000001-300010";

    // This is the ID of the Elastic Transcoder pipeline that was created when
    // setting up your AWS environment:
    // http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/sample-code.html#java-pipeline
    private static final String PIPELINE_ID = "1499175362963-8gfez2";

    private static final String INPUT_BUCKET_NAME = "insnds";

    private static final String OUTPUT_BUCKET_NAME = "outsnds";

    private static final AWSCredentials creds = new AWSCredentials() {
        @Override
        public String getAWSAccessKeyId() {
            return "";
        }

        @Override
        public String getAWSSecretKey() {
            return "";
        }
    };


    private final AmazonS3Client s3client = new AmazonS3Client( creds );

    private final AmazonSQS amazonSqs = new AmazonSQSClient( creds );

    private final AmazonElasticTranscoder amazonElasticTranscoder = new AmazonElasticTranscoderClient( creds );

    private String uploadInputSoundToS3( final byte [] in ) {

        final String s3FileName = UUID.randomUUID().toString() +".wav";
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength( in.length );
        s3client.putObject( INPUT_BUCKET_NAME, s3FileName, new ByteArrayInputStream(in), metadata );

        return s3FileName;
    }

    /**
     *
     * @param audio
     * @return
     */
    public void toMP3( final byte [] audio ) {
        try {

            final String uploadedFileKey = uploadInputSoundToS3( audio );
            final File tempDir = Files.createTempDir();
            final File f = new File(tempDir.getAbsolutePath() + "/" + uploadedFileKey );

            java.nio.file.Files.copy( new ByteArrayInputStream(audio), f.toPath(), StandardCopyOption.REPLACE_EXISTING );
            convert(uploadedFileKey, tempDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void convert( final String uploadedFileKey, final File tempDir ) throws Exception {
        SqsQueueNotificationWorker sqsQueueNotificationWorker = new SqsQueueNotificationWorker(amazonSqs, SQS_QUEUE_URL);
        Thread notificationThread = new Thread(sqsQueueNotificationWorker);
        notificationThread.start();

        // Create a job in Elastic Transcoder.
        CreateJobResult res = createElasticTranscoderJob( uploadedFileKey );

        // Wait for the job we created to complete.
        System.out.println("Waiting for job to complete: " + res.getJob().getId());
        waitForJobToComplete(res, sqsQueueNotificationWorker, tempDir);
        System.out.println();
    }


    private CreateJobResult createElasticTranscoderJob( final String uploadedFileKey ) throws Exception {

        // Setup the job input using the provided input key.
        JobInput input = new JobInput()
                .withKey(uploadedFileKey);

        // Setup the job output using the provided input key to generate an output key.
        List<CreateJobOutput> outputs = new ArrayList<CreateJobOutput>();
        CreateJobOutput output = new CreateJobOutput()
                .withKey(TranscoderSampleUtilities.inputKeyToOutputKey(uploadedFileKey))
                .withPresetId(PRESET_ID);
        outputs.add(output);

        // Create a job on the specified pipeline and return the job ID.
        CreateJobRequest createJobRequest = new CreateJobRequest()
                .withPipelineId(PIPELINE_ID)
                .withInput(input)
                .withOutputs(outputs);

        return amazonElasticTranscoder.createJob(createJobRequest);
    }

    /**
     * Waits for the specified job to complete by adding a handler to the SQS
     * notification worker that is polling for status updates.  This method
     * will block until the specified job completes.
     * @param jobId
     * @param sqsQueueNotificationWorker
     * @throws InterruptedException
     */
    private void waitForJobToComplete(final CreateJobResult result, final SqsQueueNotificationWorker sqsQueueNotificationWorker, final File tempDir) throws InterruptedException {

        // Create a handler that will wait for this specific job to complete.
        JobStatusNotificationHandler handler = new JobStatusNotificationHandler() {

            @Override
            public void handle(JobStatusNotification jobStatusNotification) throws IOException {
                if (jobStatusNotification.getJobId().equals(result.getJob().getId())) {
                    System.out.println(jobStatusNotification);

                    if (jobStatusNotification.getState().isTerminalState()) {

                        String outKey = result.getJob().getOutput().getKey();
                        S3Object object = s3client.getObject(OUTPUT_BUCKET_NAME, outKey);

                        S3ObjectInputStream is = object.getObjectContent();

                        File f = new File(tempDir.getAbsolutePath() + "/" + outKey + ".mp3");

                        java.nio.file.Files.copy( is, f.toPath(), StandardCopyOption.REPLACE_EXISTING );

                        synchronized(this) {
                            this.notifyAll();
                        }
                    }
                }
            }
        };
        sqsQueueNotificationWorker.addHandler(handler);

        // Wait for job to complete.
        synchronized(handler) {
            handler.wait();
        }

        // When job completes, shutdown the sqs notification worker.
        sqsQueueNotificationWorker.shutdown();
    }
}
