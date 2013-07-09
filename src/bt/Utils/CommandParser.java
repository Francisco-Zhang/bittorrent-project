package bt.Utils;

import java.util.Scanner;

import bt.Model.Bittorrent;
import bt.Model.Peer;
import bt.Model.Server;

/**
 * It will statically handle commands and parse them while the client runs.
 * @author Ike, Robert and Fernando
 *
 */

public class CommandParser {
	
	/** 
	 * Main entry point for parsing commands.
	 * @param String command
	 */
	
	/**
	 * This dummy variable to access Peer class static methods
	 */
	static Peer agent = new Peer();
	
	public static void execute(String command) throws Exception {
		switch(command) {
		case "run":
			agent.handShake();
			break;
		case "quit":
			Utilities.callClose();
		case "help":
			CommandParser.printHelp();
			break;
		case "serverstatus":
			if(Server.getInstance().getServerStatus()) {
				System.out.println("Server bounded to client");
			} else {
				System.out.println("Server unbounded listening for incoming connections");
			}
			break;
		case "printpeers":
			Bittorrent.getInstance().printPeerList();
			break;
		case "connect":
			String read = null;
			int peer = -1;
			System.out.println("\nInput peer number: ");
			Bittorrent.getInstance().printPeerList();
			System.out.print("PEER NUMBER >> ");
			Scanner sc = new Scanner(System.in);
			try {
				peer = Integer.parseInt(sc.nextLine());
				Bittorrent.getInstance().connectToPeer(peer);
				// call Bittorrent client method to connect to peer.
			} catch (Exception e) { System.out.println(e.getMessage()); }
			break;
		default:
			throw new IllegalArgumentException("Invalid command, input help for commands.");
		}
	}
	
	/**
	 * Prints a list of available commands. It will expand as we advance.
	 */
	public static void printHelp() {
		System.out.println("\nHELP ---------------------------");
		System.out.println("help - Available Commands (so far)");
		System.out.println("connect - Select a peer and attempt connection.");
		System.out.println("printpeers - Print list of available peers");
		System.out.println("serverstatus - server's bound status.");
		System.out.println("quit - terminates the client.");
		System.out.println("--------------------------------\n");
	}
	
}
