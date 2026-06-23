package com.docconverter.application.processing;

import java.time.Duration;

public interface RetryBackoffPolicy {

    Duration delayAfterFailure(int failedAttemptNumber);
}
