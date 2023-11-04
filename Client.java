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
		int segCount = 0;

		
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
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); // place in memory where we can write bytes
				ObjectOutputStream os = new ObjectOutputStream(outputStream); // allows us to write objects to the output stream (this serializes objects, i.e. converts them to bytes)
				os.writeObject(segment); // writes the segment ByteArrayOutputStream through the ObjectOutputStream (takes segment Object, serializes it, and writes it to the output stream)
				byte[] sendData = outputStream.toByteArray(); // copies contents from ByteArrayOutputStream buffer to a byte array
				// create and send the packet
				DatagramPacket packet = new DatagramPacket(sendData, sendData.length, IPAddress, portNumber);
				System.out.println("SENDER: Sending segment: sq:" + currentSeg + ", size:" + charsRead + ", checksum:" + segment.getChecksum() + ", content: (" + payload + ")");
				socket.send(packet);
				

				// wait for ack before sending next segment
				byte[] receiveData = new byte[1024]; // buffer to store received data
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length); // packet to store received data
				System.out.println("SENDER: Waiting for ACK");
				socket.receive(receivePacket); // receive data from the socket and store it in the packet
				ByteArrayInputStream inputStream = new ByteArrayInputStream(receiveData); // place in memory where we can read bytes. takes received data and puts it in the buffer
				ObjectInputStream is = new ObjectInputStream(inputStream); // deserializes the received data and converts it to an object
				Segment ackSegment = (Segment) is.readObject(); // reads the object and casts it to a Segment object so we can access its fields
				// check if the ack is correct (type and sequence number)
				if (ackSegment.getType() == SegmentType.Ack && ackSegment.getSq() == currentSeg) {
					System.out.println("SENDER: Received ACK for segment " + currentSeg);
					System.out.println("------------------------------------------------------------------");
				} else {
					System.out.println("ERROR: Received incorrect ACK");
					System.out.println("Expected ACK: " + currentSeg + " Received ACK: " + ackSegment.getSq());
					System.out.println("Expected Type: " + SegmentType.Ack + " Received Type: " + ackSegment.getType());
				}
				// switch segment number
				currentSeg = (currentSeg + 1) % 2;
				segCount += 1;
            }
			if (charsRead == -1) {
				System.out.println("SENDER: Total segments sent: " + segCount);
			}
        } catch (Exception e) {
            e.printStackTrace();

        }
	} 

	/* TODO: This function is essentially the same as the sendFileNormal function
	 *      except that it resends data segments if no ACK for a segment is 
	 *      received from the server.*/
	public void sendFileWithTimeOut(int portNumber, InetAddress IPAddress, File file, float loss) {
		final int TIMEOUT = 1000; // Timeout in milliseconds
		final int RETRY_LIMIT = 5; // Number of retries before exiting transmission
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT); // Sets blocking time for socket.receive() calls (default is infinite)
        } catch (SocketException e) {
            e.printStackTrace();
        }

        int currentSeg = 0;
		int segCount = 0;
        int retries = 0; // To count consecutive retries

        try (Reader reader = new FileReader(file)) { // FileReader remembers its position in the file
            char[] chars = new char[4];
            int charsRead; // will be 4 unless we're at the end of the file

            while ((charsRead = reader.read(chars)) != -1) { // reads up to 4 characters into charsRead. remembers position. if chars is empty, returns -1
                String payload = new String(chars, 0, charsRead); // saves the read characters as a string
                Segment segment = new Segment();
                segment.setPayLoad(payload);
                segment.setSq(currentSeg);
                segment.setType(SegmentType.Data);
                segment.setSize(charsRead);
                

                boolean ackReceived = false;
                while (!ackReceived && retries < RETRY_LIMIT) {
					// Simulate corruption
					boolean corrupted = isCorrupted(loss);
					segment.setChecksum(checksum(payload, corrupted));
		
                    try {
                        // Send the segment
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); 
                        ObjectOutputStream os = new ObjectOutputStream(outputStream); 
                        os.writeObject(segment); 
                        byte[] sendData = outputStream.toByteArray();
                        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, IPAddress, portNumber);
						System.out.println("SENDER: Sending segment: sq:" + currentSeg + ", size:" + charsRead + ", checksum:" + segment.getChecksum() + ", content: (" + payload + ")");
                        if (corrupted) {
							System.out.println("Corruption has occurred in segment " + currentSeg);
							System.out.println("\t\t>>>>>>> NETWORK ERROR: segment checksum is corrupted <<<<<<<<<");
						}
						socket.send(packet);

                        // Attempt to receive the ACK
                        byte[] receiveData = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
						System.out.println("SENDER: Waiting for ACK");
                        socket.receive(receivePacket);
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(receiveData);
                        ObjectInputStream is = new ObjectInputStream(inputStream);
                        Segment ackSegment = (Segment) is.readObject();

                        if (ackSegment.getType() == SegmentType.Ack && ackSegment.getSq() == currentSeg) {
                            System.out.println("SENDER: Received ACK for segment " + currentSeg);
							System.out.println("------------------------------------------------------------------");
                            ackReceived = true;
                            retries = 0; // reset retries since we successfully received ACK
                        } else {
                            throw new IOException("SENDER: Received incorrect ACK");
                        }
                    } catch (SocketTimeoutException e) {
						retries++;
                        System.out.println("SENDER: TIMEOUT! Resending segment " + currentSeg + " (current retry count: " + retries + ")");
                    }
                }

                if (retries >= RETRY_LIMIT) {
                    System.out.println("Reached retry limit for segment " + currentSeg + ". Exiting transmission.");
                    break;
                }

                // Increment the segment number
                currentSeg = (currentSeg + 1) % 2;
				segCount += 1;
            }
			if (charsRead == -1) {
				System.out.println("SENDER: Total segments sent: " + segCount);
			}
        } catch (IOException e) {
            System.err.println("IOException occurred: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Class not found while reading ACK: " + e.getMessage());
        } finally {
            if (socket != null) { // close the socket if it's still open
                socket.close();
            }
        }
    }


}