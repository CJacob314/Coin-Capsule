/* Copyright (C) Jacob Cohen - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Jacob Cohen <jcohen30@uic.edu> or <jacob@jacobcohen.info>
 */
package com.cjacob314.apps;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.wallet.SendRequest;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jacob Cohen <jcohen30@uic.edu> or <jacob@jacobcohen.info>
 */
public class Server {
	private static Server inst = null;
	private static String indexContent = null;
	private static ScheduledExecutorService exec = null;

	private Server(){
		exec = Executors.newScheduledThreadPool(3);
		exec.scheduleAtFixedRate(() -> {
			File queue = new File("/root/Bitcoin-Queued-Sends");

			if(queue.exists()){
				try {
					BufferedReader reader = new BufferedReader(new FileReader(queue));

					String firstLine = reader.readLine();
					if(firstLine != null && !firstLine.isBlank()){
						BufferedWriter writer = new BufferedWriter(new FileWriter(queue,false));
						writer.write("");

						String[] items = firstLine.split(" ");

						var btcWallet = BitcoinWallet.getWallet();
						Address toAddr = Address.fromString(btcWallet.getParams(), items[0]);
						JLogger.log("Received order to send " + items[1] + "BTC to " + items[0]);
						long toSend = (long)(Double.parseDouble(items[1]) * 100000000L - 1250);
						//System.out.println("toSend: " + toSend);
						SendRequest req = SendRequest.to(toAddr, Coin.valueOf(toSend));
						//System.out.println("request made");
						req.ensureMinRequiredFee = true;
						req.recipientsPayFees = false;
						//System.out.println("booleans set");
						Wallet.SendResult res = btcWallet.sendCoins(req);
						//System.out.println("btcWallet.sendCoins(req) completed");
						String hashId = res.tx.getTxId().toString();
						//System.out.println("Got hash of request");
						var future = res.broadcast.broadcast();
						//System.out.println("received future");
						JLogger.log("Order to send " + toSend + "Satoshis has been sent to the blockchain with hash " + hashId + ".");

						var transaction = future.get(); // Make this thread wait
						JLogger.log("Transaction sent with hash" + transaction.getTxId().toString() + ".");
					}
				} catch(IOException e) {
					e.printStackTrace();
				} catch(InsufficientMoneyException | InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}

			}
		}, 0, 30, TimeUnit.SECONDS);
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


	private static String readAllBytesUntilPacketEnd(InputStream is, String[] content) throws IOException {
		ArrayList<Character> charList = new ArrayList<>();
		Stack<Character> lastFour = new Stack<>();

		while(true){
			char c = (char)is.read();
			if (c == '\r' || c == '\n')
				lastFour.push(c);
			else lastFour.clear();

			charList.add(c);

			String lastFourStr = lastFour.toString().replaceAll("(?im)\\[|,| |\\]", "");
			if(lastFourStr.equals("\r\n\r\n")) {
				String firstChunk = charList.toString().replaceAll("(, )|\\[|\\]", "");
				int indexOfCL = firstChunk.indexOf("Content-Length: ") + "Content-Length: ".length();
				if(indexOfCL >= "Content-Length: ".length()) {

					Scanner tempScanner = new Scanner(firstChunk.substring(indexOfCL));
					int CL = tempScanner.nextInt();
					tempScanner.close();
					List<Character> lastList = new ArrayList<>();
					for(int i = 0; i < CL; i++) {
						lastList.add((char) is.read());
					}
					content[0] = lastList.toString().replaceAll("(, )|\\[|\\]", "");
				}
				break;
			}
		}

		return charList.toString().replaceAll("(, )|\\[|\\]", "");
	}

	public static void startMultiThreaded(InetSocketAddress address) {

		try (var serverSocket = getServerSocket(address)) {

			URL whatismyip = new URL("http://checkip.amazonaws.com"); // to get our external
			BufferedReader in = new BufferedReader(new InputStreamReader(
					whatismyip.openStream()));

			String ip = in.readLine(); //you get the IP as a String

			JLogger.log("Started multi-threaded server hosted on " + ip);

			// A cached thread pool with a limited number of threads
			var threadPool = newCachedThreadPool(96);

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
						      // Writing to the output stream and then closing it
						      // sends data to the client
						      var writer = new BufferedWriter(new OutputStreamWriter(
								      socket.getOutputStream(), encoding.name()));
						      var w = socket.getOutputStream();
						) {
							String[] contentIfApplicable = {"hello there you!"};
							String entireReq = readAllBytesUntilPacketEnd(socket.getInputStream(), contentIfApplicable); // My specially made function as no EOFs, it reads headers and content perfectly (so far)
							int firstEndLine = entireReq.indexOf("\n");
							if(firstEndLine == -1) return;

							String firstLine = entireReq.substring(0, firstEndLine);

							Pattern regex = Pattern.compile("GET /([a-zA-Z0-9/_-]*\\.(css|png|jpg|js|ico)).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
							Matcher matcher = regex.matcher(firstLine);
							if (matcher.find()) {
								String fileName = matcher.group(1);
								byte[] bytes = Files.readAllBytes(Path.of("./" + fileName));
								String fileType = "";

								if(firstLine.contains(".png")) fileType = "image/png";
								else if(firstLine.contains(".css")) fileType = "text/css";
								else if(firstLine.contains(".js")) fileType = "text/javascript";
								else if(firstLine.contains(".jpg")) fileType = "image/jpeg";
								else if(firstLine.contains(".ico")) fileType = "image/x-icon";

								String resHeader = "HTTP/1.1 200 OK\r\n" +
										String.format("Content-Length: %d\r\n", bytes.length) +
										String.format("Content-Type: %s; charset=%s\r\n\r\n", fileType, "UTF-8");

								w.write(resHeader.getBytes(StandardCharsets.UTF_8));
								w.flush();
								w.write(bytes);
								w.flush();
								socket.close();
							}
							else if(firstLine.contains("GET / HTTP/1.1")) {
								writer.write(getResponse(encoding));
								writer.flush();
								socket.close();
							} else if(firstLine.contains("POST")) {
								var postedData = splitQuery(contentIfApplicable[0]);
								Wallet btcWallet = BitcoinWallet.getWallet();
								AtomicReference<Date> sendingDate = new AtomicReference<>();
								sendingDate.set(null);

								if(postedData.containsKey("request_addr")) {
									byte[] bytes = (btcWallet.freshReceiveAddress().toString()).getBytes(StandardCharsets.UTF_8);
									String resHeader = "HTTP/1.1 200 OK\r\n" +
											String.format("Content-Length: %d\r\n", bytes.length) +
											String.format("Content-Type: %s; charset=%s\r\n\r\n", "text/plain", "UTF-8");
									w.write(resHeader.getBytes(StandardCharsets.UTF_8));
									w.write(bytes);
									writer.flush();
									socket.close();
								} else if(postedData.containsKey("completed_transaction_hash")) {
									final String[] rejectReason = {null};
									final boolean[] foundTransaction = {false};

									JLogger.log("Received completed transaction request. Testing for validity...");
									btcWallet.getTransactions(false).forEach(t -> {
										if(t.getTxId().toString().equals(postedData.get("completed_transaction_hash"))) {
											foundTransaction[0] = true;

											long transValue = t.getValueSentToMe(btcWallet).value;
											if(transValue > 0 && !t.isPending()) {
												UUID jobID = UUID.randomUUID();
												// TODO Add to a SQL database or .csv file

												var inst = Calendar.getInstance();
												int minutes = inst.get(Calendar.MINUTE);
												int hours = inst.get(Calendar.HOUR_OF_DAY);
												if(minutes < 59) minutes++;
												else {
													minutes = 0;
													if(hours < 24) hours++;
													else {
														JLogger.log("Really bad, could not schedule");
														// TODO send an email to me jacob@jacobcohen.info or something saying this failed with some information!
													}
												}
												String[] resDateChunks = postedData.get("date").split("-");
												try {
													var format = new SimpleDateFormat("yyyy-MM-dd");
													Date date = format.parse(postedData.get("date"));
													sendingDate.set(date);
													Date yesterday = new Date(new Date().getTime() - 86400000L);
													//System.out.println(LocalDateTime.now().toString());
													if(!date.after(yesterday)) {
														JLogger.log("INVALID DATE!");
														rejectReason[0] = "invalidDate";
													} else {
														String cmd = String.format("sudo echo 'SHELL=/bin/bash\n%d %d %s %s * [[ $(date \"+\\%%Y\") == %s ]] && echo \"%s %s\" > /root/Bitcoin-Queued-Sends 2>&1' | sudo crontab -u root -",
																minutes, hours, resDateChunks[2], resDateChunks[1], resDateChunks[0],
																postedData.get("rec_addr"), t.getValue(btcWallet).toFriendlyString().replace(" BTC", "")), s;

													File temp = File.createTempFile("temp", null);
													PrintWriter pw = new PrintWriter(temp);
													pw.println("#!/bin/bash");
													pw.println(cmd);
													pw.close();


													ProcessBuilder pb = new ProcessBuilder("/bin/bash", temp.toString());
													int exitVal = pb.start().waitFor();

													JLogger.log("Validated successfully (passed). Scheduling the cronjob exited with exit code " + exitVal);
													}
												} catch(IOException | InterruptedException | ParseException e) {
													e.printStackTrace();
												}
											} else {
												rejectReason[0] = "pendingOrZero";
											}
										} else {
											if(!foundTransaction[0]) rejectReason[0] = "notFound";
										}
									});

									if(rejectReason[0] == null) {
										rejectReason[0] = postedData.get("rec_addr") + " on " + sendingDate.get().toString().replace("00:00:00 CDT", "") + ". Enjoy and good luck!";
									}
									byte[] bytes = (rejectReason[0] + "\r\n").getBytes(StandardCharsets.UTF_8);
									String resHeader = "HTTP/1.1 200 OK\r\n" +
											String.format("Content-Length: %d\r\n", bytes.length) +
											String.format("Content-Type: %s; charset=%s\r\n\r\n", "text/plain", "UTF-8");
									try {
										w.write(resHeader.getBytes(StandardCharsets.UTF_8));
										w.write(bytes);
										w.flush();
										socket.close();
									} catch(IOException e) {
										e.printStackTrace();
									}

								}

								exec.scheduleAtFixedRate(() -> {
									JLogger.log("Total of Coin Capsule wallet is " + btcWallet.getBalance().toFriendlyString());
								}, 0, 30, TimeUnit.MINUTES);
							} else{
								// Removed stdout logging due to so many garbage bot requests
							}

						} catch (IOException e) {
							// not important
						}
					});
				} catch (IOException e) {
					JLogger.log("Exception while handling connection");
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			JLogger.log("Could not create socket at " + address);
			e.printStackTrace();
		}
	}

	private static String getResponse(Charset encoding) {
		String body = Server.getIndexContent();
		var contentLength = body.getBytes(encoding).length;

		return "HTTP/1.1 200 OK\r\n" +
				String.format("Content-Length: %d\r\n", contentLength) +
				String.format("Content-Type: text/html; charset=%s\r\n",
				encoding.displayName()) + "\r\n" + body;
	}

	private static String getIndexContent() {
		if(indexContent == null) {
			try {
				indexContent = new String(Files.readAllBytes(Path.of("./Home.html")), StandardCharsets.UTF_8);
			} catch(IOException e) {
				JLogger.log("Error reading from Home.html file!");
				e.printStackTrace();
				return null;
			}
		}
		return indexContent;
	}

	private static ExecutorService newCachedThreadPool(int maximumNumberOfThreads) {
		return new ThreadPoolExecutor(0, maximumNumberOfThreads,
				60L, TimeUnit.SECONDS,
				new SynchronousQueue<>());
	}

	private static ServerSocket getServerSocket(InetSocketAddress address)
			throws Exception {
		// Backlog is the maximum number of pending connections on the socket
		int backlog = 0;

		String keyStorePathStr = Path.of(Server.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParent() + "/keystore.jks";
		Path keyStorePath = Path.of(keyStorePathStr);

		char[] keyStorePassword = new String(Files.readAllBytes(Path.of("./pwd.pwd")), StandardCharsets.UTF_8).toCharArray(); // Not hardcoded for obvious reasons

		// Bind
		var serverSocket = getSslContext(keyStorePath, keyStorePassword)
				.getServerSocketFactory()
				.createServerSocket(address.getPort(), backlog, address.getAddress());

		// We don't need the password anymore, overwrite it
		Arrays.fill(keyStorePassword, '0');
		return serverSocket;
	}

	private static SSLContext getSslContext(Path keyStorePath, char[] keyStorePass)
			throws Exception {

		// JKS generated from the pem files from Let's Encrypt
		var keyStore = KeyStore.getInstance("JKS");
		keyStore.load(new FileInputStream(keyStorePath.toFile()), keyStorePass);

		var keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
		keyManagerFactory.init(keyStore, keyStorePass);

		var sslContext = SSLContext.getInstance("TLS");

		sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
		return sslContext;
	}

	public static Server getServer(){
		if(inst == null) inst = new Server();

		return inst;
	}
}
