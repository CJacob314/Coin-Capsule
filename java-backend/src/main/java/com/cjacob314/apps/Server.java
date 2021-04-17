/* Copyright (C) Jacob Cohen - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Jacob Cohen <jcohen30@uic.edu> or <jacob@jacobcohen.info>
 */
package com.cjacob314.apps;

import org.bitcoinj.core.Coin;
import org.bitcoinj.wallet.Wallet;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jacob Cohen <jcohen30@uic.edu> or <jacob@jacobcohen.info>
 */
public class Server {
	private static Server inst = null;
	private static String indexContent = null;

	private Server(){
		startMultiThreaded(new InetSocketAddress("0.0.0.0", 443));
	}

	private static Map<String, String> splitQuery(String response) throws UnsupportedEncodingException {
		Map<String, String> query_pairs = new LinkedHashMap<String, String>();
		String[] pairs = response.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
		}
		return query_pairs;
	}

	public static void startMultiThreaded(InetSocketAddress address) {

		try (var serverSocket = getServerSocket(address)) {

			URL whatismyip = new URL("http://checkip.amazonaws.com");
			BufferedReader in = new BufferedReader(new InputStreamReader(
					whatismyip.openStream()));

			String ip = in.readLine(); //you get the IP as a String

			System.out.println("Started multi-threaded server hosted on " + ip);

			// A cached thread pool with a limited number of threads
			var threadPool = newCachedThreadPool(8);

			var encoding = StandardCharsets.UTF_8;

			// This infinite loop is not CPU-intensive since method "accept" blocks
			// until a client has made a connection to the socket
			while (true) {
				try {
					var socket = serverSocket.accept();
					// Create a response to the request on a separate thread to
					// handle multiple requests simultaneously
					threadPool.submit(() -> {

						try ( // Use the socket to read the client's request
						      var reader = new BufferedReader(new InputStreamReader(
								      socket.getInputStream(), encoding.name()));
						      // Writing to the output stream and then closing it
						      // sends data to the client
//						      var byteWriter = new BufferedOutputStream(socket.getOutputStream());
						      var writer = new BufferedWriter(new OutputStreamWriter(
								      socket.getOutputStream(), encoding.name()));
						      var w = socket.getOutputStream();


						) {
							PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.{png,jpg,css,js}");

							List<String> content = new ArrayList<>();
							List<String> headerLines = getHeaderLines(reader, content);
							Pattern regex = Pattern.compile("GET /([a-zA-Z0-9/_]*\\.(css|png|jpg|js)).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
							Matcher matcher = regex.matcher(headerLines.get(0));
							if (matcher.find()) {
//								System.out.println("Received get for a css png jpg or js file");
								String fileName = matcher.group(1);
								byte[] bytes = Files.readAllBytes(Path.of("./" + fileName));
								String fileType = "";
								// Don't make files called css_name.jpg LOL
								if(headerLines.get(0).contains("png")) fileType = "image/png";
								else if(headerLines.get(0).contains("css")) fileType = "text/css";
								else if(headerLines.get(0).contains("js")) fileType = "text/javascript";
								else if(headerLines.get(0).contains("jpg")) fileType = "image/jpeg";
//								fileType = "text/plain";
								var tempRes = "hello did this work?".getBytes(StandardCharsets.UTF_8);
//								bytes = tempRes;
								String resHeader = "HTTP/1.1 200 OK\r\n" +
										String.format("Content-Length: %d\r\n", bytes.length) +
										String.format("Content-Type: %s; charset=%s\r\n\r\n", fileType, "UTF-8");

								w.write(resHeader.getBytes(StandardCharsets.UTF_8));
								w.flush();
								w.write(bytes);
								w.flush();
								socket.close();
							}
							else if(headerLines.contains("GET / HTTP/1.1")) {
//								System.out.println("Received get for root, sending Home.html");
								writer.write(getResponse(encoding));
								writer.flush();
								// We're done with the connection → Close the socket
								socket.close();
							} else if(headerLines.get(0).contains("POST")){
//								System.out.println("Got post");
//								content.forEach(System.out::println);
//								var postedData = splitQuery(headerLines.get(0).replace("POST /", "").replace("HTTP/1.1", "")); // I know kinda bad not kinda really
								var postedData = splitQuery(content.get(0));

								Wallet btcWallet = BitcoinWallet.getWallet();

								ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
//								postedData.forEach((k,v) -> System.out.println(k + ": " + v));
								if(postedData.containsKey("request_addr")) {
									byte[] bytes = (btcWallet.freshReceiveAddress().toString()).getBytes(StandardCharsets.UTF_8);
									String resHeader = "HTTP/1.1 200 OK\r\n" +
											String.format("Content-Length: %d\r\n", bytes.length) +
											String.format("Content-Type: %s; charset=%s\r\n\r\n", "text/plain", "UTF-8");
									w.write(resHeader.getBytes(StandardCharsets.UTF_8));
									w.write(bytes);
									writer.flush();
									socket.close();
								}
								else if(postedData.containsKey("completed_transaction_hash")){
									String resHeader = "HTTP/1.1 200 OK\r\n\r\n";
									w.write(resHeader.getBytes(StandardCharsets.UTF_8));
									w.flush();
									socket.close();
									System.out.println("Received completed transaction request");
									btcWallet.getTransactions(false).forEach(t -> {
										System.out.println("Looping through TxId: " + t.getTxId().toString());
										System.out.println("from post data hash is: " + postedData.get("completed_transaction_hash"));
										if(t.getTxId().toString().equals(postedData.get("completed_transaction_hash"))){
											System.out.println("They Equal! We found one!");
											long transValue = t.getValueSentToMe(btcWallet).value;
											System.out.println(transValue);
											System.out.println(t.isPending());
											if(transValue > 0 && !t.isPending()){
												UUID jobID = UUID.randomUUID();
												// TODO Add to a SQL database!
//												File cronFile = new File("/etc/cron.d/" + jobID.toString());
//												PrintWriter pw = null;
//
//												try {
//													pw = new PrintWriter(new FileWriter("/etc/cron.d/" + jobID.toString().replace("-", "_"),true));
////													cronFile.createNewFile();
//													System.out.println("Created cronFile");
//												} catch(IOException e) {
//													e.printStackTrace();
//												}
//												if(pw != null && !pw.checkError()){
//													FileWriter cronW = new FileWriter(cronFile);
												var inst = Calendar.getInstance();
												int minutes = inst.get(Calendar.MINUTE);
												int hours = inst.get(Calendar.HOUR_OF_DAY);
												if(minutes < 59) minutes++;
												else{
													minutes = 0;
													if(hours < 24) hours++;
													else {
														System.err.println("Really bad, could not schedule");
														// TODO send an email to me jacob@jacobcohen.info or something saying this failed with some information!
													}
												}
//												postedData.forEach((k,v) -> System.out.println(k + ": " + v));
												System.out.println(postedData.get("date"));
												String[] resDateChunks = postedData.get("date").split("-");
												System.out.println("Made it through after the finding first hash");
//												pw.println(String.format("%d %d %s %s * root [[ $(date \"+%%Y\") == %s ]] && java -jar /root/java-webserv/WebServer-0.1-SNAPSHOT-jar-with-dependencies.jar -initSendTo %s %s", minutes, hours, resDateChunks[2], resDateChunks[1], resDateChunks[0], postedData.get("rec_addr"), t.getValue(btcWallet).toFriendlyString().replace(" BTC", "")));
//												pw.println("SHELL=/bin/bash");
//												pw.println(String.format("%d %d %s %s * root [[ $(date \"+\\%%Y\") == %s ]] && echo '%s %s' >> /root/Bitcoin-Queued-Sends", minutes, hours, resDateChunks[2], resDateChunks[1], resDateChunks[0], postedData.get("rec_addr"), t.getValue(btcWallet).toFriendlyString().replace(" BTC", "")));
//												pw.close();
												try {
													String cmd = String.format("sudo echo 'SHELL=/bin/bash\n%d %d %s %s * [[ $(date \"+\\%%Y\") == %s ]] && echo \"%s %s\" >> /root/Bitcoin-Queued-Sends 2>&1' | sudo crontab -u root -",
															minutes, hours, resDateChunks[2], resDateChunks[1], resDateChunks[0],
															postedData.get("rec_addr"), t.getValue(btcWallet).toFriendlyString().replace(" BTC", "")), s;
													System.out.println(cmd);
//													var process = Runtime.getRuntime().exec(cmd);


													File temp = File.createTempFile("temp",null);
													PrintWriter pw = new PrintWriter(temp);
													pw.println("#!/bin/bash");
													pw.println(cmd);
													pw.close();

													ProcessBuilder pb = new ProcessBuilder("/bin/bash", temp.toString()).inheritIO();
													int exitVal = pb.start().waitFor();
//													BufferedReader br = new BufferedReader(
//															new InputStreamReader(process.getInputStream()));
//													while ((s = br.readLine()) != null)
//														System.out.println("line: " + s);
//													int exitVal = process.waitFor();
													System.out.println("Scheduling exited with exit code " + exitVal);
//													process.destroy();
												} catch(IOException | InterruptedException e) {
													e.printStackTrace();
												}
//												cronW.write(String.format("%d %d %d %d * ? %d root 'java -jar /root/java-webserv/WebServer-0.1-SNAPSHOT-jar-with-dependencies.jar '", minutes, hours, resDateChunks[1], resDateChunks[2], resDateChunks[0]));
//												cronW.flush();
//												cronW.close();
												return;
//												}
											}
										}
									});

//									btcWallet.getTransactions(false).forEach(t -> {
//										long transValue = t.getValueSentToMe(btcWallet).value;
//										if(transValue > 0 && !t.isPending()){
//											UUID jobID = UUID.randomUUID();
//											// TODO Add to a SQL database!
//											File cronFile = new File("/etc/cron.d/" + jobID);
//											try {
//												cronFile.createNewFile();
//											} catch(IOException e) {
//												e.printStackTrace();
//											}
//											if(cronFile.exists()){
//												try {
//													FileWriter cronW = new FileWriter(cronFile);
//
//													var inst = Calendar.getInstance();
//													int minutes = inst.get(Calendar.MINUTE);
//													if(minutes < 59) minutes++;
//													String[] resDateChunks = postedData.get("date").split("-");
//													cronW.write(String.format("%d %d %d %d * ? %d root 'java -jar /root/java-webserv/WebServer-0.1-SNAPSHOT-jar-with-dependencies.jar '", minutes, inst.get(Calendar.HOUR_OF_DAY), resDateChunks[1], resDateChunks[2], resDateChunks[0]));
//													return;
//												} catch(IOException e) {
//													e.printStackTrace();
//												}
//											}
//										}
//									});
								}

								exec.scheduleAtFixedRate(() -> {
									System.out.println("You have " + btcWallet.getBalance().toFriendlyString());
								}, 0, 30, TimeUnit.MINUTES);


//								btcWallet.getWalletTransactions().forEach(trans -> {
//									if(!trans.getTransaction().isPending())
//										System.err.printf("Transaction %s is no longer pending. Amount %sBTC\n", trans.getTransaction().toString(), trans.getTransaction().getValueSentToMe(btcWallet).toFriendlyString());
//								});

							}else if(headerLines.contains("GET /image.png HTTP/1.1")){
								System.out.println("Got image.png requests!");
								byte[] bytes = Files.readAllBytes(Path.of("./image.png"));
								writer.write("HTTP/1.1 200 OK\r\n" +
										String.format("Content-Length: %d\r\n", bytes.length) +
										String.format("Content-Type: document; charset=%s\r\n"));
//								writer.write(bytes.toString());
								writer.flush();
								socket.close();
							}
							else if(headerLines.contains("GET /favicon.ico HTTP/1.1")){

							}
							else{
//								System.out.println("Received Different\n" + headerLines.get(0));
							}

						} catch (IOException e) {
							System.err.println("Exception while creating response");
//							e.printStackTrace();
						}
					});
				} catch (IOException e) {
					System.err.println("Exception while handling connection");
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			System.err.println("Could not create socket at " + address);
			e.printStackTrace();
		}
	}

	private static String getResponse(Charset encoding) {
//		var body = "The server says hi 👋\r\n";
		String body = Server.getIndexContent();
		var contentLength = body.getBytes(encoding).length;

		return "HTTP/1.1 200 OK\r\n" +
				String.format("Content-Length: %d\r\n", contentLength) +
				String.format("Content-Type: text/html; charset=%s\r\n",
						encoding.displayName()) +
				// An empty line marks the end of the response's header
				"\r\n" +
				body;
	}

	private static String getResponseCustom(Charset encoding, String body){
		var contentLength = body.getBytes(encoding).length;

		return "HTTP/1.1 200 OK\r\n" +
				String.format("Content-Length: %d\r\n", contentLength) +
				String.format("Content-Type: text/html; charset=%s\r\n",
						encoding.displayName()) +
				// An empty line marks the end of the response's header
				"\r\n" +
				body;
	}

	private static String getIndexContent() {
		if(indexContent == null) {
			try {
				indexContent = new String(Files.readAllBytes(Path.of("./Home.html")), StandardCharsets.UTF_8);
			} catch(IOException e) {
				System.err.println("Error reading from Home.html file!");
				e.printStackTrace();
				return null;
			}
		}
		return indexContent;
	}

	private static List<String> getHeaderLines(BufferedReader reader, List<String> content) throws IOException {
		var lines = new ArrayList<String>();
		var line = reader.readLine();
		// An empty line marks the end of the request's header
		while (!line.isEmpty()) {
			lines.add(line);
			line = reader.readLine();
		}
		while(reader.ready()){
			line = reader.readLine();
			content.add(line);
			if(line == null) break;
		}
		return lines;
	}

	private static List<String> getContentLines(BufferedReader reader) throws IOException{
		System.out.println("getContentLines WAS CALLED");
		var lines = new ArrayList<String>();
		reader.reset();
		var line = reader.readLine();
		// An empty line marks the end of the request's header
//		for(; !line.isEmpty(); line = reader.readLine());
		System.out.println("past the reader.ReadLine()");
		int n = 0;
		while (n < 100) {
			System.out.println("in while loop");
			lines.add(line);
			line = reader.readLine();
			System.out.println("read line: " + line);
			n++;
			System.out.println("n++ fam");
		}
		return lines;
	}

	private static ExecutorService newCachedThreadPool(int maximumNumberOfThreads) {
		return new ThreadPoolExecutor(0, maximumNumberOfThreads,
				60L, TimeUnit.SECONDS,
				new SynchronousQueue<>());
	}

	private static ServerSocket getServerSocket(InetSocketAddress address)
			throws Exception {

		// Backlog is the maximum number of pending connections on the socket,
		// 0 means that an implementation-specific default is used
		int backlog = 0;

//		var keyStorePath = Path.of(Server.class.getClassLoader().getResource("keystore.jks").toURI());
//		var keyStorePath = ((Path.of(Server.class.getProtectionDomain().getCodeSource().getLocation().toURI().toString()).getParent().toUri().toString() + "/keystore.jks").replace("file:", ""));
		String keyStorePathStr = Path.of(Server.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParent() + "/keystore.jks";
		Path keyStorePath = Path.of(keyStorePathStr);
//		System.out.println("keyStorePath: " + keyStorePath.toString());

		char[] keyStorePassword = new String(Files.readAllBytes(Path.of("./pwd.pwd")), StandardCharsets.UTF_8).toCharArray();

		// Bind the socket to the given port and address
		var serverSocket = getSslContext(keyStorePath, keyStorePassword)
				.getServerSocketFactory()
				.createServerSocket(address.getPort(), backlog, address.getAddress());

		// We don't need the password anymore → Overwrite it
		Arrays.fill(keyStorePassword, '0');

		return serverSocket;
	}

	private static SSLContext getSslContext(Path keyStorePath, char[] keyStorePass)
			throws Exception {

		var keyStore = KeyStore.getInstance("JKS");
		keyStore.load(new FileInputStream(keyStorePath.toFile()), keyStorePass);

		var keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
		keyManagerFactory.init(keyStore, keyStorePass);

		var sslContext = SSLContext.getInstance("TLS");

		// Null means using default implementations for TrustManager and SecureRandom
		sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
		return sslContext;
	}

	public static Server getServer(){
		if(inst == null) inst = new Server();

		return inst;
	}
}
