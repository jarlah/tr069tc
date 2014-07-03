package com.owera.tr069client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.BindException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.log4j.Logger;

import com.owera.tr069client.monitor.Status;

public class HttpHandler {

	private String serialNumber;

	private int serialNumberInt;

	private static String[] urls;

	private HttpClient client;

	private static Random random = new Random(System.currentTimeMillis());

	private static Logger logger = Logger.getLogger(Session.class);

	//	private HttpConnectionManagerParams hcmp;

	public HttpHandler(Arguments args) {

		//		HttpConnectionManager connectionManager = new SimpleHttpConnectionManager();
		HttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
		//		hcmp = new HttpConnectionManagerParams();
		//		hcmp.setStaleCheckingEnabled(true);
		//		int totConn = args.getNumberOfThreadsPrStep()*args.getNumberOfSteps();
		//		hcmp.setMaxTotalConnections(totConn);
		client = new HttpClient(connectionManager);
		//		client.getParams().setParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, new Boolean(false));
		client.getParams().setParameter(HttpClientParams.USE_EXPECT_CONTINUE, new Boolean(true));
		client.getParams().setParameter(HttpConnectionParams.CONNECTION_TIMEOUT, 2000);
		//		HostConfiguration hc = client.getHostConfiguration();
		//		String[] urls = args.getProvUrl().split(",");
		//		int totConnPrHost = totConn/urls.length + 1;
		//		hcmp.setMaxConnectionsPerHost(hc, totConnPrHost);
		//		connectionManager.setParams(hcmp);
	}

	private String chooseUrl(Arguments args) {
		if (urls == null) {
			urls = args.getProvUrl().split(",");
		}
		int index = serialNumberInt % urls.length;
		return urls[index];
	}

	public String send(String request, Arguments args, Status status, String action) throws IOException {

		long startSend = System.currentTimeMillis();
		HttpMethodRetryHandler retryhandler;
		retryhandler = new RetryHandler(status);
		client.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, retryhandler);
		String url = chooseUrl(args);
		PostMethod pm = new PostMethod(url);
		if (request != null) {
			RequestEntity requestEntity = new StringRequestEntity(request, "text/xml", "ISO-8859-1");
			pm.setRequestEntity(requestEntity);
		}

		boolean retry = true;
		int statusCode = HttpStatus.SC_OK;
		int executionCount = 0;
		long execTime = 0;
		while (retry) {
			try {
				pm = authenticate(pm, url, args);
				long start = System.currentTimeMillis();
				statusCode = client.executeMethod(pm);
				execTime = System.currentTimeMillis() - start;
				logger.debug("The " + action + "-request execution time: " + execTime + " ms. (serialnumber: " + serialNumber + ")");
				retry = false;
			} catch (BindException be) { // BindException is NOT handled by the RetryHandler!!
				long delay = Util.getRetrySleep(executionCount++);
				retry = true;
				try {
					status.incRetryOccured(status.getCurrentOperation());
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} catch (Throwable t) {
				retry = false;
			}
		}

		if (statusCode != HttpStatus.SC_OK) {
			if (statusCode != HttpStatus.SC_NO_CONTENT)
				logger.error("The HTTP response from the server is NOT ok: " + statusCode + " (serialnumber: " + serialNumber + ")");
			return "";
		}

		Reader reader = new InputStreamReader(pm.getResponseBodyAsStream(), pm.getResponseCharSet());
		BufferedReader br = new BufferedReader(reader);
		StringBuilder sb = new StringBuilder();
		while (true) {
			String line = br.readLine();
			if (line == null)
				break;
			sb.append(line);
		}
		String response = sb.toString();

		//		String response = pm.getResponseBodyAsString();
		if (response == null) {
			System.out.println(url);
			System.out.println(statusCode);
		} else {
			bitrateSleep(request, response, args);
		}
		pm.releaseConnection();
		long endSend = System.currentTimeMillis();
		logger.info("Time not spent on send/receive in send() " + ((endSend - startSend) - execTime) + " ms. (serialnumber: " + serialNumber + ")");
		return response;
	}

	private PostMethod authenticate(PostMethod pm, String urlStr, Arguments args) throws MalformedURLException {
		URL url = new URL(urlStr);
		List<String> authPrefs = new ArrayList<String>(2);
		authPrefs.add(AuthPolicy.DIGEST);
		authPrefs.add(AuthPolicy.BASIC);
		client.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, authPrefs);
		Credentials defaultcreds = new UsernamePasswordCredentials("000000-TR069TestClient-" + serialNumber, "001122334455");
		client.getState().setCredentials(new AuthScope(url.getHost(), url.getPort(), pm.getHostAuthState().getRealm()), defaultcreds);
		pm.setDoAuthentication(true);
		args.setAuthenticate(true);
		return pm;
	}

	private void bitrateSleep(String request, String response, Arguments args) {
		if (args.getBitRate() != Integer.MAX_VALUE) {
			long sleepTm;
			int totalBits;
			if (request != null) {
				totalBits = (request.getBytes().length + response.getBytes().length) * 8;
				sleepTm = (long) (((float) totalBits / args.getBitRate()) * 1000);
			} else {
				totalBits = (response.getBytes().length) * 8;
				sleepTm = (long) (((float) totalBits / args.getBitRate()) * 1000);
			}
			sleepTm += (long) 1500f * (100000f / ((float) args.getBitRate()));
			if (sleepTm > 0) {
				try {
					sleepTm = randomizeSleep(sleepTm);
					Thread.sleep(sleepTm);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Let the sleep time change with +/- 33%, at least when there is some
	 * significant sleep-intervall.
	 */
	private long randomizeSleep(long sleep) {
		if (sleep > 3)
			return sleep;
		int maxRandomintervall = (int) sleep * 33 / 100;
		boolean positiv = random.nextBoolean();

		int randomIntervall = random.nextInt(maxRandomintervall + 1);
		if (positiv)
			return sleep + randomIntervall;
		else
			return sleep - randomIntervall;
	}

	public String getSerialNumber() {
		return serialNumber;
	}

	public void setSerialNumber(String serialNumber) {
		this.serialNumber = serialNumber;
	}

	public int getSerialNumberInt() {
		return serialNumberInt;
	}

	public void setSerialNumberInt(int serialNumberInt) {
		this.serialNumberInt = serialNumberInt;
	}

}
