package com.glebzhebrakov.elastictranscoderdemo.model;

import java.io.IOException;

public interface JobStatusNotificationHandler {

    public void handle(JobStatusNotification jobStatusNotification) throws IOException;
}
