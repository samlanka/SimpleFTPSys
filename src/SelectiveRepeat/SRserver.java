package SelectiveRepeat;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Scanner;


class recSegment{
	int id;
	String data;
	recSegment next;

	public recSegment(int id, String data){
		this.id = id;
		this.data = data;
		this.next = null;
	}
}

public class SRserver{
	
	private static recSegment head;
	
	public SRserver() {
		head = null;
	}

	private static int decimal(String substring){
		//Convert string to decimal integer value
		int decimalValue = Integer.parseInt(substring, 2);
		return decimalValue;
	}

	public static int calcChecksum(byte[] buf) {
		//Return a 16-bit checksum for a byte array payload (decimal)
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
	    return decimal(String.format("%16s", Integer.toBinaryString(checksum)).replace(' ', '0'));
	}

	public static boolean matchChecksum(int newChecksum, int oldChecksum){
		//Return True if two checksums are equal, else False
		newChecksum = Integer.parseInt("FFFF", 16) - newChecksum;
		long test = newChecksum + oldChecksum;
		test =  Integer.parseInt("FFFF", 16) - test;
		return (test == 0);
	}

	public static byte[] sendAck(int num){
		//Creates ACK Packet
		String header = Integer.toBinaryString(num);
		for(int i = header.length(); i < 32; i++)
			header = "0" + header;
		header = header + "00000000000000001010101010101010";
		return header.getBytes();
	}

	public static void main(String[] args){
		//Enter user inputs
		Scanner s = new Scanner(System.in);
		System.out.println("Enter the server port number:");
		int port = s.nextInt();
		System.out.println("Enter the path of the output file:");
		String outputFile = s.next();
		System.out.println("Enter the probability of loss:");
		double probLoss =  s.nextDouble();
		s.close();
		
		//Create server socket
		DatagramSocket serverSocket = null;		
		try{
			serverSocket = new DatagramSocket(port);
			}
		catch (SocketException e1){
			System.out.println("I am here!!");
			e1.printStackTrace();
		}

		int count = 0;
		ByteArrayOutputStream msgBuffer = new ByteArrayOutputStream();
		DatagramPacket fromClient = null;

		while (true){
			try{
				byte[] dataPacket = new byte[2048];
				//Receive data packet from Client
				fromClient = new DatagramPacket(dataPacket, dataPacket.length);
				serverSocket.receive(fromClient);
		
				String data = new String(fromClient.getData());
				
				//Extract sequence num, checksum, packetType and message from received packet
				int seqNum = decimal(data.substring(0,32));
				System.out.println("Received packet " + seqNum);
				int oldChecksum = decimal(data.substring(32,48));
				String dataType = data.substring(48,64);
				String message = data.substring(64, data.length());
				
				//calculate new checksum on message and compare
				int newChecksum = calcChecksum(message.getBytes());
				boolean matchCS = matchChecksum(newChecksum, oldChecksum);

				double randLoss = Math.random();

				if (dataType.equals("0000000000000000")) 
					//EOF Packet
						break;

				if (randLoss <= probLoss){
					System.out.println("Packet loss, sequence number = " + seqNum);
					continue;
				}

				else if (matchCS){
					//Packet received correctly
					
					int portNum = fromClient.getPort();
					InetAddress IPadd = fromClient.getAddress();
					byte[] ackPacket = sendAck(seqNum);
					DatagramPacket toClient = new DatagramPacket(ackPacket, ackPacket.length, IPadd, portNum);
					serverSocket.send(toClient);
					System.out.println("Sent ACK " + seqNum);
					
					if(seqNum == count) {
						msgBuffer.write(message.getBytes());
						System.out.println("Writing packet "+count);
						count++;
						if (head != null) {
							recSegment temp = head;
							while (temp != null && temp.id != count) {
								System.out.println("Writing packet"+temp.id);
								msgBuffer.write(temp.data.getBytes());
								head = head.next;
								temp = temp.next;
								count++;
							}
						}
				
					}
					else if (seqNum > count) {
						recSegment newSeg = new recSegment(seqNum, message);
						if (head == null)
							head = newSeg;
						else {
							recSegment temp = head;
							recSegment prev = head;
							while (temp.next != null && temp.id < seqNum) {
								prev = temp;
								temp = temp.next;
							}
							if (temp.id < seqNum){
								temp.next = newSeg;
							}
							else {
								if(prev != temp)
									prev.next = newSeg;
								else
									head = newSeg;
								newSeg.next = temp;
							}
						}
					}
				}

			}
			catch (Exception e) {
				System.err.println(e);
			}

		}
		serverSocket.close();
		
		//Create file and flush buffer data to file
		FileOutputStream file = null;
		try {
			file = new FileOutputStream(outputFile);
			msgBuffer.writeTo(file);
			file.close();
			System.out.println("File created!");
		} catch( Exception e) {
			e.printStackTrace();
		}
	}

}

