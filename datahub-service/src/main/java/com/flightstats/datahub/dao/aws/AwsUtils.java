package com.flightstats.datahub.dao.aws;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.retry.RetryUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;

import java.io.IOException;

/**
 *
 */
public class AwsUtils {

    public static boolean isAwsError(AmazonClientException exception) {
        if (exception.getCause() instanceof IOException) return true;

        if (exception instanceof AmazonServiceException) {
            AmazonServiceException ase = (AmazonServiceException)exception;

            if (ase.getStatusCode() >= HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                return true;
            }

            //com.amazonaws.services.s3.model.AmazonS3Exception: Status Code: 400, AWS Service: Amazon S3,
            // AWS Error Code: RequestTimeout, AWS Error Message: Your socket connection to the server was not read from or written to within the timeout period. Idle connections will be closed.
            if (StringUtils.contains(ase.getErrorCode(), "RequestTimeout")) {
                return true;
            }

            /*
             * Clock skew exception. If it is then we will get the time offset
             * between the device time and the server time to set the clock skew
             * and then retry the request.
             */
            if (RetryUtils.isClockSkewError(ase)) return true;
        }

        return false;
    }

    public static boolean isAwsThrottling(AmazonClientException exception) {
        if (exception instanceof AmazonServiceException) {
            AmazonServiceException ase = (AmazonServiceException)exception;

            return RetryUtils.isThrottlingException(ase);
        }
        return false;
    }
}
