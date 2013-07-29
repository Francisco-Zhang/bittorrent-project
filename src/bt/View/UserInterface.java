package bt.View;



import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Scanner;

import bt.Exceptions.NotifyPromptException;
import bt.Model.Bittorrent;
import bt.Model.Peer;
import bt.Utils.CommandParser;
import bt.Utils.Utilities;

/**
 * The class represents the UI between the user and the client.
 * @author Isaac Yochelson, Robert Schomburg and Fernando Geraci
 *
 */
public class UserInterface implements Runnable {
	
	private Bittorrent bittorrent;
	private Peer peer=null;
	private static UserInterface instance = null;
	
	/**
	 * Constructs a UserInterface instance.
	 * @param bt
	 */
	private UserInterface(Bittorrent bt) {
		this.bittorrent = bt;
		// Program's loop
		System.out.println("Connection Successful, welcome");
		System.out.println("Input help for commands");
		Thread UIThread = new Thread(this);
		UIThread.start();
	}
	
	/**
	 * Instance getter.
	 * @return UserInterface instance.
	 */
	public static UserInterface getInstance() {
		if(UserInterface.instance == null) {
			throw new IllegalAccessError("UserInterface never instantiated.");
		}
		return UserInterface.instance;
	}
	
	public void run() {
		try {
			// 1. Connect to peers
			bittorrent.connectToPeer("128.6.171.3:6916");
			bittorrent.connectToPeer("128.6.171.4:6929");
			
			// 2. wait for getting unchoked.
			while(bittorrent.peersChoked()) {
				System.out.println("-- Waiting for peers to unchoke.");
				try {
					Thread.sleep(1500);
				} catch(Exception e) {
					e.getMessage();
				}
			}
			int peerListSize = bittorrent.getPeerList().size();

			// 3. start bitfields queue
			bittorrent.downloadAlgorithm();
			try {
				this.clientLoop();
			} catch (Exception e) { 
				System.err.println(e.getMessage());	
			}		
		} catch (Exception e) { // FATAL ERROR 
			
			Utilities.callClose();
			System.out.println(e.getMessage());	}
	}
	
	/**
	 * Alternative Instance getter.
	 * @return UserInterface instance.
	 */
	public static UserInterface getInstance(Bittorrent bt) {
		if(UserInterface.instance == null) {
			UserInterface.instance = new UserInterface(bt);
		}
		return UserInterface.instance;
	}
	
	/**
	 * This method will be the main input loop for the text based controller.
	 */
	private void clientLoop() {
		
		Scanner sc = new Scanner(System.in);
		while(true) {
			try {
				System.out.print("\n%> ");
				String nextLine = sc.nextLine();
				CommandParser.execute(nextLine);
			} catch(NotifyPromptException ne) {
				System.out.println(ne.getMessage());
				System.out.print("%> ");
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}
	}
	
	/**
	 * Received a notification.
	 * @param String message
	 */
	public void receiveEvent(String message) {
		System.out.println("\n"+message);
		System.out.print("%> ");
	}
}
