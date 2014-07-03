package com.owera.tr069client;

import java.io.IOException;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;

import com.owera.tr069client.monitor.Status;

public class RetryHandler implements HttpMethodRetryHandler {

	private Status status;

	public RetryHandler(Status status) {
		this.status = status;
	}

	public boolean retryMethod(HttpMethod method, IOException ex, int executionCount) {
		status.incRetryOccured(status.getCurrentOperation());
		long delay = Util.getRetrySleep(executionCount);
		if (delay == -1)
			return false;
		try {
			int previousDelay = status.getRetrySleep();
			status.setRetrySleep((int)delay + previousDelay);
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}

}
