package SelectiveRepeat;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Scanner;


class segment{
	int id;
	String data;
	segment next;

	public segment(int id, String data){
		this.id = id;
		this.data = data;
		this.next = null;
	}
}

public class SRclient{
	private static segment head;

	public SRclient(){
		head = null;
	}

	private static int decimal(String substring){
		//Convert string to decimal integer value
		int decimalValue = Integer.parseInt(substring, 2);
		return decimalValue;
	}

	
	public static String calcChecksum(byte[] buf) {
		//Return a 16-bit checksum for a byte array payload (binary string)
		int length = buf.length;
	    int i = 0;
	    int sum = 0;
	    while (length > 0) {
	        sum += (buf[i++]&0xff) << 8;
	        if ((--length)==0) break;
	        sum += (buf[i++]&0xff);
	        --length;
	    }

	    int checksum =  (~((sum & 0xFFFF)+(sum >> 16)))&0xFFFF;
	    return String.format("%16s", Integer.toBinaryString(checksum)).replace(' ', '0');

	}
	
	public static byte[] generateHeader(int seqNumInt, byte[] segData) {
		//Create segment header containing sequence num, checksum and dataPacket type
		String seqNum = Integer.toBinaryString(seqNumInt);
		String checksum = calcChecksum(segData);
		for (int i = seqNum.length(); i < 32; i++) {
			seqNum = "0" + seqNum;
		}
		String header = seqNum + checksum + "0101010101010101";
		return header.getBytes();
	}

	public static int generateSegments(byte[] fileData, int MSS) {
		int numPackets = (int) Math.ceil((double) fileData.length / MSS);		
		String data = new String(fileData);
		for (int segNum = 0; (segNum < numPackets) && ((segNum * MSS) < data.length()); segNum++) {
			int segEnd = Math.min((MSS * (segNum + 1)), data.length());
			String segData = data.substring(MSS * segNum, segEnd);
			segment s = new segment(segNum, segData);
			if (head == null) {
				head = s;
			} 
			else {
				segment temp = head;
				while (temp.next != null) 
					temp = temp.next;
				temp.next = s;
			}
		}
		return numPackets;
	}

	public static int getACKNum(byte[] data) {
		String ACK = "";
		for (int i = 0; i < 64; i++) {
			if (data[i] == 48) {
				ACK += "0";
			} else {
				ACK += "1";
			}
		}
		String dataType = ACK.substring(48, 64);
		if (dataType.equals("1010101010101010")) {
			return decimal(ACK.substring(0, 32));
		}
		return -1;
	}

	public static void main(String[] args) //throws IOException
	{
		Scanner s = new Scanner(System.in);
		System.out.println("Enter the server ip address:");
		String host = s.next();
		System.out.println("Enter the port number of server:");
		int port = s.nextInt();
		System.out.println("Enter the path of the file to be transfered:");
		String fileName  = s.next();
		System.out.println("Enter the window size:");
		int N = s.nextInt();
		System.out.println("Enter the maximum segment size:");
		int MSS = s.nextInt();
		s.close();
		
		int[] marker = new int[N];
		DatagramSocket clientSocket = null;
		InetAddress serverIP = null;
		byte[] fileData = null;
		
		try {
			fileData = Files.readAllBytes(new File(fileName).toPath());
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		try{
			clientSocket = new DatagramSocket(); //port
			}
		catch (SocketException e1){
			e1.printStackTrace();
		}

		try{
			serverIP = InetAddress.getByName(host);
		}
		catch (UnknownHostException e1){
			e1.printStackTrace();
		}

		int nPackets = generateSegments(fileData, MSS);

		int curSeg = 0;
		int segPointer = 0;
		long tic = System.currentTimeMillis();
		int ackSeqNum = -1;
		int m = 0;
		
		while(curSeg < nPackets){
			for(m=0; m < N; m++) {
				if (curSeg > nPackets)
					break;
				if(marker[m] == 2) {
					curSeg++;
					continue;
				}
			
				segment temp = head;
				while((temp.next!=null) && (temp.id != curSeg))
					temp = temp.next;
		
				String segData = temp.data;
				byte[] segBytes = null;
				segBytes = segData.getBytes();
				byte[] segHeader = generateHeader(curSeg, segBytes);

				byte[] packet = new byte[segHeader.length + segBytes.length];
				System.arraycopy(segHeader, 0, packet, 0, segHeader.length);
				System.arraycopy(segBytes, 0, packet, segHeader.length, segBytes.length);

				DatagramPacket toReceiver = new DatagramPacket(packet, packet.length, serverIP, port);
				
				try {
					clientSocket.send(toReceiver);
					System.out.println("Packet sent:" + curSeg);
					curSeg++;
					marker[m] = 1;
				} 
				catch (IOException e){
					e.printStackTrace();
				}
			}
			System.out.println("Current packet: " + curSeg + " with m: " + m);
			int timeout = 1000;// in milliseconds
			byte[] receive = new byte[1536]; //WHY??????
			DatagramPacket fromReceiver = new DatagramPacket(receive, receive.length);
	
			curSeg = curSeg - m;
			
			try {
				clientSocket.setSoTimeout(timeout);
				while (true) {
					clientSocket.receive(fromReceiver);
					ackSeqNum = getACKNum(fromReceiver.getData());
					System.out.println("ACK received: " + ackSeqNum);
					if (ackSeqNum != -1) {
						segPointer = ackSeqNum - curSeg;
						marker[segPointer] = 2;
						if(segPointer == 0) {
							while(marker[segPointer]==2) {
								for (int i =1; i<N;i++) {
									marker[i-1] = marker[i];
								}
								marker[N-1] = -1;
								curSeg++;
							}
						}
					}
					
				}
			} 
			catch (IOException ste) {// timeout
				System.out.println("Timeout, sequence number = " + ackSeqNum);
				curSeg = ackSeqNum + 1;
				segPointer = 0;
			} 
			
		}
		//Send EOF Packet
		String EOF = "000000000000000000000000000000000000000000000000000000000000000000000000000";
		byte[] EOFbytes = EOF.getBytes();
		DatagramPacket eofPacket = new DatagramPacket(EOFbytes, EOFbytes.length, serverIP, port);
		
		try {
			clientSocket.send(eofPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		long toc = System.currentTimeMillis();
		System.out.println("Total transfer time is " + (toc - tic) + " ms");
	}
}