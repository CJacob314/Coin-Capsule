/* Copyright (C) Jacob Cohen - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Jacob Cohen <jcohen30@uic.edu> or <jacob@jacobcohen.info>
 */
package com.cjacob314.apps;

import org.bitcoin.Secp256k1Context;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * @author Jacob Cohen <jcohen30@uic.edu> or <jacob@jacobcohen.info>
 */
public class BitcoinWallet {
	private WalletAppKit bitcoin;

	private BitcoinWallet(){
		NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
		try {
			File appDataDirectory = new File(Path.of(Server.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParent().toString());
			bitcoin = new WalletAppKit(params, Script.ScriptType.P2WPKH, null, appDataDirectory, "BitcoinLocker-" + params.getPaymentProtocolId()){
				@Override
				protected void onSetupCompleted(){
					// TODO
				}
			};

			// Does get to here
			if(params == RegTestParams.get())
				bitcoin.connectToLocalHost();
			System.out.println("connected to localhost");
			bitcoin.setBlockingStartup(false).setUserAgent("BitcoinLocker", "0.1");
			System.out.println("setBlocking and setUserAgent");

//			PeerGroup pg = new PeerGroup(params, bitcoin.chain());
//			pg.addWallet(bitcoin.wallet());
//			pg.start();
			bitcoin.startAsync().awaitRunning();
			System.out.println("bitcoin service is running...");
//			System.err.println("startAsync called and awaited");
//			bitcoin.wallet().getIssuedReceiveKeys().forEach(k -> {
//				System.out.println(k.getPublicKeyAsHex());
//			});

//			System.out.println(bitcoin.wallet().freshReceiveAddress().toString());
//			System.out.println(bitcoin.wallet().getKeyChainSeed().toString());
		} catch(URISyntaxException e) {
			e.printStackTrace();
		}

	}

	private static BitcoinWallet inst = null;

	public static Wallet getWallet(){
		if(inst == null) inst = new BitcoinWallet();

		return inst.bitcoin.wallet();
	}

	public static Wallet getPayWallet(){
		return new BitcoinWallet().bitcoin.wallet();
	}

}
