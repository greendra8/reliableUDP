import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.net.*;
import java.util.Scanner;
import java.io.ObjectInputStream;


import javax.xml.crypto.Data;

public class Client {
	DatagramSocket socket;
	static final int RETRY_LIMIT = 4;	/* 
	 * UTILITY METHODS PROVIDED FOR YOU 
	 * Do NOT edit the following functions:
	 *      exitErr
	 *      checksum
	 *      checkFile
	 *      isCorrupted  
	 *      
	 */

	/* exit unimplemented method */
	public void exitErr(String msg) {
		System.out.println("Error: " + msg);
		System.exit(0);
	}	

	/* calculate the segment checksum by adding the payload */
	public int checksum(String content, Boolean corrupted)
	{
		if (!corrupted)  
		{
			int i; 
			int sum = 0;
			for (i = 0; i < content.length(); i++)
				sum += (int)content.charAt(i);
			return sum;
		}
		return 0;
	}


	/* check if the input file does exist */
	File checkFile(String fileName)
	{
		File file = new File(fileName);
		if(!file.exists()) {
			System.out.println("SENDER: File does not exists"); 
			System.out.println("SENDER: Exit .."); 
			System.exit(0);
		}
		return file;
	}


	/* 
	 * returns true with the given probability 
	 * 
	 * The result can be passed to the checksum function to "corrupt" a 
	 * checksum with the given probability to simulate network errors in 
	 * file transfer 
	 */
	public boolean isCorrupted(float prob)
	{ 
		double randomValue = Math.random();   
		return randomValue <= prob; 
	}



	/*
	 * The main method for the client.
	 * Do NOT change anything in this method.
	 *
	 * Only specify one transfer mode. That is, either nm or wt
	 */

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length < 5) {
			System.err.println("Usage: java Client <host name> <port number> <input file name> <output file name> <nm|wt>");
			System.err.println("host name: is server IP address (e.g. 127.0.0.1) ");
			System.err.println("port number: is a positive number in the range 1025 to 65535");
			System.err.println("input file name: is the file to send");
			System.err.println("output file name: is the name of the output file");
			System.err.println("nm selects normal transfer|wt selects transfer with time out");
			System.exit(1);
		}

		Client client = new Client();
		String hostName = args[0];
		int portNumber = Integer.parseInt(args[1]);
		InetAddress ip = InetAddress.getByName(hostName);
		File file = client.checkFile(args[2]);
		String outputFile =  args[3];
		System.out.println ("----------------------------------------------------");
		System.out.println ("SENDER: File "+ args[2] +" exists  " );
		System.out.println ("----------------------------------------------------");
		System.out.println ("----------------------------------------------------");
		String choice=args[4];
		float loss = 0;
		Scanner sc=new Scanner(System.in);  


		System.out.println ("SENDER: Sending meta data");
		client.sendMetaData(portNumber, ip, file, outputFile); 

		if (choice.equalsIgnoreCase("wt")) {
			System.out.println("Enter the probability of a corrupted checksum (between 0 and 1): ");
			loss = sc.nextFloat();
		} 

		System.out.println("------------------------------------------------------------------");
		System.out.println("------------------------------------------------------------------");
		switch(choice)
		{
		case "nm":
			client.sendFileNormal (portNumber, ip, file);
			break;

		case "wt": 
			client.sendFileWithTimeOut(portNumber, ip, file, loss);
			break; 
		default:
			System.out.println("Error! mode is not recognised");
		} 


		System.out.println("SENDER: File is sent\n");
		sc.close(); 
	}


	/*
	 * THE THREE METHODS THAT YOU HAVE TO IMPLEMENT FOR PART 1 and PART 2
	 * 
	 * Do not change any method signatures 
	 */

	/* TODO: send metadata (file size and file name to create) to the server 
	 * outputFile: is the name of the file that the server will create
	*/
	public void sendMetaData(int portNumber, InetAddress IPAddress, File file, String outputFile) {
		// initialize the socket
		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		MetaData metaData = new MetaData();
		metaData.setName(outputFile);
		metaData.setSize((int) file.length());
		try {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(outputStream);
            os.writeObject(metaData);
            byte[] sendData = outputStream.toByteArray();
            DatagramPacket packet = new DatagramPacket(sendData, sendData.length, IPAddress, portNumber);
            socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
		socket.close();
	}


	/* TODO: Send the file to the server without corruption*/

	public void sendFileNormal(int portNumber, InetAddress IPAddress, File file) {
		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		// work out how many segments we are going to send
		int currentSeg = 0;

		
		try (Reader reader = new FileReader(file)) {
            char[] chars = new char[4];
            int charsRead;

            // Read the file 4 characters at a time
            while ((charsRead = reader.read(chars)) != -1) {
                String payload = new String(chars, 0, charsRead);
                Segment segment = new Segment();
				segment.setPayLoad(payload);
				segment.setSq(currentSeg);
				segment.setType(SegmentType.Data);
				segment.setSize(charsRead);
				segment.setChecksum(checksum(payload, false));
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				ObjectOutputStream os = new ObjectOutputStream(outputStream);
				os.writeObject(segment);
				byte[] sendData = outputStream.toByteArray();
				DatagramPacket packet = new DatagramPacket(sendData, sendData.length, IPAddress, portNumber);
				System.out.println("Sending segment " + currentSeg);
				socket.send(packet);
				

				// wait for ack before sending next segment
				byte[] receiveData = new byte[1024];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				socket.receive(receivePacket);
				ByteArrayInputStream inputStream = new ByteArrayInputStream(receiveData);
				ObjectInputStream is = new ObjectInputStream(inputStream);
				Segment ackSegment = (Segment) is.readObject();
				if (ackSegment.getType() == SegmentType.Ack && ackSegment.getSq() == currentSeg) {
					System.out.println("Received ACK for segment " + currentSeg);
				} else {
					System.out.println("Received NAK for segment " + currentSeg);
					System.out.println("Expected ACK: " + currentSeg + " Received ACK: " + ackSegment.getSq());
					System.out.println("Expected Type: " + SegmentType.Ack + " Received Type: " + ackSegment.getType());
				}
				// increment the segment number
				currentSeg = (currentSeg + 1) % 2;

				
            }
        } catch (Exception e) {
            e.printStackTrace();

        }
	} 

	/* TODO: This function is essentially the same as the sendFileNormal function
	 *      except that it resends data segments if no ACK for a segment is 
	 *      received from the server.*/
	public void sendFileWithTimeOut(int portNumber, InetAddress IPAddress, File file, float loss) {
		exitErr("sendFileWithTimeOut is not implemented");
	} 


}