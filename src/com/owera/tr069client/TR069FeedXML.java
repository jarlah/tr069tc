package com.owera.tr069client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionParams;

public class TR069FeedXML {

	private static PostMethod authenticate(String acsUser, String acsPass, HttpClient client, PostMethod pm, String urlStr) throws MalformedURLException {
		URL url = new URL(urlStr);
		List<String> authPrefs = new ArrayList<String>(2);
		authPrefs.add(AuthPolicy.DIGEST);
		authPrefs.add(AuthPolicy.BASIC);
		client.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, authPrefs);
		Credentials defaultcreds = new UsernamePasswordCredentials(acsUser, acsPass);
		client.getState().setCredentials(new AuthScope(url.getHost(), url.getPort(), pm.getHostAuthState().getRealm()), defaultcreds);
		pm.setDoAuthentication(true);
		return pm;
	}

	public static String getRequest(String tr069Method) throws IOException {
		FileReader fr = new FileReader("customtest/" + tr069Method + ".xml");
		BufferedReader br = new BufferedReader(fr);
		String line = null;
		String request = "";
		while ((line = br.readLine()) != null) {
			request += line;
		}
		return request;
	}

	/**
	 * This is a test class to allow you to simulate one single TR-069 client
	 * by feeding the XML request by request through standard input. Speeding 
	 * up the feeding process may be considered at a later stage (feeding from 
	 * files).
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			HttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
			HttpClient client = new HttpClient(connectionManager);
			client.getParams().setParameter(HttpClientParams.USE_EXPECT_CONTINUE, new Boolean(true));
			client.getParams().setParameter(HttpConnectionParams.CONNECTION_TIMEOUT, 2000);

			String url = "http://localhost:8080/xapstr069";
			PostMethod pm = new PostMethod(url);
			// ACSUser and ACSPass
			String acsUser = "tr-069";
			String acsPass = "tr-069";
			pm = authenticate(acsUser, acsPass, client, pm, url);

			// The XML to be sent to the server
			String request = getRequest("Inform");
			if (request != null) {
				RequestEntity requestEntity = new StringRequestEntity(request, "text/xml", "ISO-8859-1");
				pm.setRequestEntity(requestEntity);
			}
			int statusCode = client.executeMethod(pm);

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

			System.out.println("StatusCode: " + statusCode);
			pm.releaseConnection();
			System.out.println("Response:\n" + response);
		} catch (Throwable t) {
			System.out.println("An error occurred: " + t);
		}
	}

	/*
	 * Process:
	 * Read Inform.xml - send (show) - receive (show)
	 * 
	 */

}
