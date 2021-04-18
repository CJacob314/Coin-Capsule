/* Copyright (C) Jacob Cohen - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Jacob Cohen <jcohen30@uic.edu> or <jacob@jacobcohen.info>
 */
package com.cjacob314.apps;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
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
			bitcoin.setBlockingStartup(false).setUserAgent("BitcoinLocker", "0.3");

			bitcoin.startAsync().awaitRunning();
			JLogger.log("bitcoin service is now running...");

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
