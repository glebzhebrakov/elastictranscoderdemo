# elastictranscoderdemo
Playing with Amazon Elastic Transcoder DEMO

1. Fill AudioConversionService.java with your parameters and put credentials to      

        @Override
        public String getAWSAccessKeyId() {
            return "";
        }

        @Override
        public String getAWSSecretKey() {
            return "";
        }
        
 This should be extracted to yml file later but now we have what we have.       
2. Send POST request to http://127.0.0.1:8080/rest/api/convert with the form data file in the "file" param and Content-Type: multipart/form-data.
