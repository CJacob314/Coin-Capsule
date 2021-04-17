package com.cjacob314.apps;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.wallet.SendRequest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class AppMain
{

    private static final String SEND_COMMAND = "-initSendTo";

    private static void sendCase(String[] mainArgs){
        List<String> args = new ArrayList<>(Arrays.asList(mainArgs));
        int index = args.indexOf(SEND_COMMAND);
        var btcWallet = BitcoinWallet.getWallet();
        System.out.println("args index +1: " + args.get(index + 1));
        Address toAddr = Address.fromString(btcWallet.getParams(), args.get(index + 1));
        File weDidItDebug = new File("/root/we_DID_IT" + new Random().nextInt() % 1000);
        try {
            weDidItDebug.createNewFile();
            FileWriter w = new FileWriter(weDidItDebug);
            w.write("DEBUG MODE: But would have sent " + args.get(index + 2) + "BTC to " + args.get(index + 1) + "\n");
            w.flush();
            w.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
        System.err.println("DEBUG MODE: But would have sent " + args.get(index + 2) + "BTC to " + args.get(index + 1));
//        try {
//            btcWallet.sendCoins(SendRequest.to(toAddr, Coin.parseCoin(args.get(index + 2))));
//        } catch(InsufficientMoneyException e) {
//            System.err.println("Something very wrong, there is not enough money in the system to pay out to address: " + toAddr.toString() + " a value of " + args.get(index + 2) + "BTC");
//        }
    }

    public static void main(String[] args)
    {
        if(Arrays.asList(args).contains(SEND_COMMAND)){
            sendCase(args);
        }
        else {
            Calendar.getInstance();
            System.out.println("WebServer started...");
            Server.getServer();
        }
    }
}
