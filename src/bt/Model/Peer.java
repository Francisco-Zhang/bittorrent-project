package bt.Model;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Queue;

import bt.Model.Bittorrent;
import bt.Utils.Utilities;

/**
 * Creates a connection with a peer to download a file.
 * @author Ike, Robert and Fernando
 *
 */

public class Peer implements Runnable {
	
	private byte[][] fileHeap = null;
	private byte[][] verifyHash = null;
	private boolean[] completed = null;
	private PeerListener listener = null;
	private boolean choked = true;
	private boolean interested = false;
	private Socket dataSocket = null;
	private InputStream in = null;
	private OutputStream out = null;
	private MessageDigest sha = null;
	private boolean running = true;
	private byte[] handShakeResponse;
	public boolean peerAccepted = false;
	private String IP;
	private int port;
	
	/**
	 * This field, hash, holds the 20 byte info_hash of the .Torrent file being used by the client which
	 * instantiated this object.
	 */
	private byte[] hash;
	/**
	 * This field, clientID, holds the 20 byte peer id of the client which instantiated this object.
	 */
	private byte[] clientID;
	/**
	 * interestedQueue is a maintained list of the piece requests a particular peer has made to this client.
	 * When there is space on the outgoing TCP queue, and the connection is not choked, the oldest value in
	 * this queue will be sent to the peer.
	 */
	private Queue <Integer> interestedQueue;
	
	/**
	 * This is a constructor for a Peer taking the address and port as parameters.  The address and port of a
	 * peer object are immutable during running, and therefore can only be set with this constructor.
	 * @param address Address of the peer this object represents.
	 * @param port Port on which to contact the peer which this object represents.
	 * @param hashIn This field will hold 20 byte hash of the .Torrent file being used by the client which
	 * instantiated this object.
	 * @param peerID This field will hold the 20 byte peer id of the client which instantiated this object.
	 * @param heapReference A reference to the section of the heap where the file is stored during download.
	 * @param verifyReference A reference to a byte array storing the correct SHA-1 hashes of the pieces of
	 * the file.
	 * @param completedReference A reference to a boolean array storing the completeness status of each piece
	 * of the file.
	 * @throws UnknownHostException If the address cannot be resolved to a host, this exception will be thrown.
	 * @throws IOException If a connection cannot be opened to this host, this exception will be thrown.
	 */	
	public Peer(final String address, final int port, final byte[] hashIn, final byte[] peerID,
			byte[][] heapReference, byte[][] verifyReference, boolean[] completedReference)
			throws UnknownHostException, IOException {
		this.IP = address;
		this.port = port;
		interestedQueue = new ArrayDeque <Integer> ();
		dataSocket = new Socket(address, port);
		in = dataSocket.getInputStream();
		listener = new PeerListener(this, in);
		out = dataSocket.getOutputStream();
		hash = hashIn;
		clientID = peerID;
		fileHeap = heapReference;
		verifyHash = verifyReference;
		completed = completedReference;
		try {
			sha = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// added to start a new thread on the instantiation of a peer.
		Thread peerThread = new Thread(this);
		peerThread.start();
	}
	
	/**
	 * Default constructor used for testing from CommandParser interface
	 */
	public Peer() {};
		
	/**
	 * It sets the handshake response from the peer.
	 * @param hsr
	 */
	public void setHandShakeResponse(byte[] hsr) {
		if(this.handShakeResponse == null) {
			this.handShakeResponse = hsr;
		}
	}
	
	/**
	 * This method spins off a listener thread to receive file pieces from the peer this object
	 * represents, calls for a handshake with that peer, then enters a loop in which it serves
	 * requested file pieces to that peer.
	 */
	public void run() {
		handShake();
		Thread listenerThread = new Thread(listener);
		listenerThread.run();
		while(running) {	// This is the file sending loop. 
			if (interestedQueue.isEmpty()) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					continue;
				}
			} else {
				if (interested && !choked) {
					int toSend = interestedQueue.poll();
					if (completed[toSend]) {
						send(toSend);
					}
					interestedQueue.offer(toSend);
				}
			}
		}					// This is the end of the file sending loop.
	}
	
	/**
	 * Sends a keep alive signal to the peer this object represents.
	 * @throws IOException If the system fails to send the TCP message, this exception will be thrown.
	 */
	void keepalive () throws IOException {
		byte[] b = {(byte) 0};
		out.write(b);
		out.flush();
	}
	
	/**
	 * Sends a message to the peer this object represents that it has been choked.
	 * @throws IOException If the system fails to send the TCP message, this exception will be thrown.
	 */
	void choke () throws IOException {
		byte[] b = new byte[5];
		b[0] = 0;
		b[1] = 0;
		b[2] = 0;
		b[3] = (byte) 1;
		b[4] = (byte) 0;
		out.write(b);
		out.flush();
	}
	
	/**
	 * Sends a message to the peer this object represents that it has been unchoked.
	 * @throws IOException If the system fails to send the TCP message, this exception will be thrown.
	 */
	void unChoke () throws IOException {
		byte[] b = new byte[5];
		b[0] = 0;
		b[1] = 0;
		b[2] = 0;
		b[3] = (byte) 1;
		b[4] = (byte) 1;
		out.write(b);
		out.flush();
	}
	
	/**
	 * Sends a message to the peer this object represents we are interested in data it holds.
	 * @throws IOException If the system fails to send the TCP message, this exception will be thrown.
	 */
	void showInterested() throws IOException {
		byte[] b = new byte[5];
		b[0] = 0;
		b[1] = 0;
		b[2] = 0;
		b[3] = (byte) 1;
		b[4] = (byte) 2;
		out.write(b);
		out.flush();
	}
	
	/**
	 * Sends a message to the peer this object represents that we are not interested in the data it holds.
	 * @throws IOException If the system fails to send the TCP message, this exception will be thrown.
	 */
	void showNotInterested() throws IOException {
		byte[] b = new byte[5];
		b[0] = 0;
		b[1] = 0;
		b[2] = 0;
		b[3] = (byte) 1;
		b[4] = (byte) 3;
		out.write(b);
		out.flush();
	}
	
	/**
	 * This method will notify this client that we have successfully completed the transfer of a piece of
	 * the file from some peer.  The peer this object represents will therefore be able to remove this
	 * piece from the queue of interested pieces it is maintaining for this client. 
	 * @param piece The piece of the file which has been completed.
	 * @throws IOException 
	 */
	void showFinished (int piece) throws IOException {
		byte[] message = null;
		ByteBuffer messageBuffer = ByteBuffer.allocate(9);
		messageBuffer.putInt(5).put((byte)4).putInt(piece);
		messageBuffer.get(message);
		out.write(message);
		out.flush();
	}
	
	private void send (int index) {
		int offset = 0;
		int repeat = fileHeap[index].length / 512;
		for (offset = 0; offset < repeat; offset++) {
			int begin = offset * 512;
			byte[] payload = new byte[512];
			for (int i = 0; i < 512; i++) {
				payload[i] = fileHeap[index][begin + i];
			}
			boolean sent = false;
			while (!sent) {	// Try to send this message until it succeeds.
				try {
					sendPiece (index, begin, 512, payload);
					sent = true;
				} catch (IOException e) {
					continue;
				}
			}
		}
	}
	
	/**
	 * This message sends a piece of the file to the peer this object represents.
	 * @param index Index of this piece of the file
	 * @param begin byte offset in the piece where the payload of this message begins.
	 * @param payloadSize size of the payload in bytes.
	 * @param payload byte array of the payload.
	 * @throws IOException will be thrown if the system is unable to dispatch the message.
	 */
	void  sendPiece (int index, int begin, int payloadSize, byte[] payload) throws IOException {
		int length = payloadSize + 9;
		byte[] message = null;
		ByteBuffer messageBuffer = ByteBuffer.allocate(length + 4);
		messageBuffer.putInt(length).put((byte)7).putInt(index).putInt(begin).put(payload);
		messageBuffer.get(message);
		out.write(message);
		out.flush();
	}
	
	/**
	 * This method is called by the PeerListener child of this object when a piece of the file is received
	 * from the peer this object represents.  If this piece is already marked completed, we will assume that
	 * this peer's have message has been lost, and will resend it.  If it is not marked completed, we will
	 * add this data to the fileHeap and attempt to verify that the piece is complete.
	 * @param index The index of this piece of the file.
	 * @param begin The base zero offset from the beginning of this piece where the payload begins.
	 * @param payloadSize The number of bytes in the payload.
	 * @param payload A byte array of the incoming data.
	 */
	void getPiece (int index, int begin, int payloadSize, byte[] payload) {
		if (completed[index]) {
			boolean sent = false;
			// This is a bit complicated looking, but this block attempts to send a have message every
			// 50 Milliseconds until it succeeds.
			while (!sent) {
				try {
					showFinished(index);
					sent = true;
				} catch (IOException e) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e1) {
						continue;
					}
				}
			}
		} else {
		// This loops over the bytes in payload and writes them into the file heap.
			for (int offset = 0; offset < payloadSize; ++offset) {
			fileHeap[index][begin + offset] = payload[offset];
			}
			verifySHA(index);
		}
	}

	/**
	 * This method sends a request message to the peer this object represents.
	 * @param index piece of the file to be requested.
	 * @param begin byte offset
	 * @param length byte offset
	 * @throws IOException will be thrown if the system is unable to dispatch the message.
	 */
	void requestIndex(int index, int begin, int length) throws IOException {
		byte[] message = new byte[17];
		ByteBuffer messageBuffer = ByteBuffer.allocate(17);
		messageBuffer.putInt(13).put((byte) 6).putInt(index).putInt(begin).putInt(length);
		messageBuffer.get(message);
		out.write(message);
		out.flush();
	}
	
	/**
	 * This method is called by the PeerListener child of this object when a request is received from
	 * the peer this object represents.
	 * @param index The index of the piece that the peer has requested.
	 */
	void requestReceived (int index) {
		interestedQueue.add(index);
	}
	
	public String toString() {
		return this.IP+":"+this.port;
	}
	
	/**
	 * This method is called by the PeerListener child of this object when a have is received from
	 * the peer this object represents.
	 * @param index The index of the piece that the peer has acknowledged complete..
	 */
	void haveReceived (int index) {
		interestedQueue.remove(index);
	}
	
	/**
	 * This method sends a cancel message to the peer this object represents.
	 * @param index piece of the file to be requested.
	 * @param begin byte offset
	 * @param length byte offset
	 * @throws IOException will be thrown if the system is unable to dispatch the message.
	 */
	void cancelIndex(int index, int begin, int length) throws IOException {
		Byte b = (byte) 8;
		byte[] message = new byte[17];
		ByteBuffer messageBuffer = ByteBuffer.allocate(13);
		messageBuffer.put(b).putInt(index).putInt(begin).putInt(length);
		messageBuffer.get(message);
		out.write(message);
		out.flush();
	}
	
	/**
	 * This method can be used to send a bitfield to the peer this object represents.  This should only
	 * be done as the first message to this peer.  A bitfield is a byte[] with each index that the downloader,
	 * this client, has sent set to one and the rest set to zero. Downloaders which don't have anything yet
	 * may skip the 'bitfield' message. The first byte of the bitfield corresponds to indices 0 - 7 from high
	 * bit to low bit, respectively. The next one 8-15, etc. Spare bits at the end are set to zero.
	 * @param bitfield a byte[] bitfield to form the message
	 * @throws IOException if the system fails to send the TCP packet properly, this exception will be thrown.
	 */
	void sendBitfield(byte[] bitfield) throws IOException {
		out.write(bitfield);
		out.flush();
	}
	/**
	 * Sets the interested bit flag on this peer's connection.  When a peer sets not interested we
	 * clear the interestedQueue.
	 * @param value Value for interested flag.
	 */
	void setInterested (boolean value) {
		interested = value;
		if (!value) {
			interestedQueue.clear();
		}
	}
	
	/**
	 * Sets the choked bit flag on this peer's connection.  We clear the interestedQueue when we
	 * are choked.
	 * @param value Value for the choked flag.
	 */
	void setChoke (boolean value) {
		choked = value;
		if (value) {
			interestedQueue.clear();
		}
	}
	
	/**
	 * Performs the handshake to open connection with peer.
	 */
	private void handShake() {
 		byte[] handShakeBA = new byte[68];		
		ByteBuffer handShakeBB = ByteBuffer.allocate(68);
		String btProtocol = "BitTorrent protocol";
		
		byte[] b1 = new byte[1];
		b1[0] = (byte) 19;
		byte[] b2 = new byte[8];
		for (int i = 0; i < 8; i++)
			b2[i] = (byte) 0;		
		
		handShakeBB
			.put(b1)
			.put(btProtocol.getBytes())
			.put(b2)
			.put(this.hash)
			.put(clientID);

		handShakeBA = handShakeBB.array();
		try {
			this.out.write(handShakeBA);
			this.out.flush();
		} catch (Exception e) { 
			System.err.println("Error in handshake");
			/* hope this never happens */ 
		}		
	}
	
	/**
	 * This method can be called before removing the last reference to this object to clear system
	 * resources and heap memory.
	 */
	void dispose () {
		listener.dispose();
		fileHeap = null;
		verifyHash = null;
		completed = null;
		listener = null;
		choked = true;
		interested = false;
		running = false;
		boolean closed = false;
		// This loop attempts to close dataSocket once every 50 Milliseconds until it succeeds.
		while (!closed) {
			try {
				dataSocket.close();
			} catch (IOException e) {
				try {
					Thread.sleep(50);
					closed = true;
				} catch (InterruptedException e1) {
					continue;
				}
			}
		}
		in = null;
		out = null;
		dataSocket = null;
		sha = null;
		hash = null;
		clientID = null;
		interestedQueue.clear();
		interestedQueue = null;	
	}
	
/**
 * This method verifies that the piece of the file with the given index is complete and valid.  If the
 * file is complete and valid, It will be marked complete in the completed array, and a The peer will be
 * sent a have message.
 * @param index The piece of the file being verified
 */
	private void verifySHA(int index) {
		byte[] test = sha.digest(fileHeap[index]);
		if (verifyHash[index] == test) {
			boolean sent = false;
			// This is a bit complicated looking, but this block attempts to send a have message every
			// 50 Milliseconds until it succeeds.
			while (!sent) {
				try {
					showFinished(index);
					sent = true;
				} catch (IOException e) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e1) {
						continue;
					}
				}
			}
			completed[index] = true;
		}
	}
	
	/**
	 * It does a byte match of the info_hashes.
	 * @param byte[] response
	 * @return boolean True if match, false otherwise.
	 */
	public boolean validateInfoHash(byte[] response) {
		if(!this.peerAccepted) {
			// match inf_hashes
			if(Utilities.matchBytes(
					Utilities.getInfoHashFromHandShakeResponse(response), 
					this.hash)) {
				this.peerAccepted = !this.peerAccepted;
				return this.peerAccepted;
			} else {
				System.out.println("ERROR: info_hash doesn't match, connection terminated.");
			}
		}
		return false;
	}
}

