import javax.sound.sampled.Port;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

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
	public void sendMetaData(int portNumber, InetAddress IPAddress, File file, String outputFile) throws IOException {
		MetaData metadata = new MetaData();
		metadata.setName(outputFile);
		metadata.setSize((int) file.length());
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
		objectStream.writeObject(metadata);

		byte[] data = outputStream.toByteArray();
		DatagramPacket sentPacket = new DatagramPacket(data, data.length, IPAddress, portNumber);
		socket = new DatagramSocket();
		socket.send(sentPacket);
		System.out.println(metadata);
		System.out.println("Metadata is sent");
	}


	/* TODO: Send the file to the server without corruption*/
	public void sendFileNormal(int portNumber, InetAddress IPAddress, File file) throws IOException {
		socket = new DatagramSocket();
		int size = 4;
		FileInputStream fileInputStream = new FileInputStream(file);
		byte[] buffer = new byte[(int) file.length()];
		fileInputStream.read(buffer);

		List<byte[]> segments = new ArrayList<>();
		for (int i = 0; i < file.length();) {
			byte[] segment = new byte[(int) Math.min(size, file.length() - i)];
			for (int j = 0; j < segment.length; j++, i++) {
				segment[j] = buffer[i];
			}
			segments.add(segment);
		}
		System.out.println(segments);
		int sq = 0;
		for (int i =0; i < segments.size();i++) {
			String stringData = new String(segments.get(i));
			Segment fileSeg = new Segment();
			fileSeg.setSize(segments.get(i).length);
			fileSeg.setSq(sq);
			fileSeg.setType(SegmentType.Data);
			fileSeg.setPayLoad(stringData);
			fileSeg.setChecksum(checksum(stringData, false));
			sq = 1 - sq;

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream os = new ObjectOutputStream(outputStream);
			os.writeObject(fileSeg);
			byte[] dataFile = outputStream.toByteArray();
			DatagramPacket filePacket = new DatagramPacket(dataFile, dataFile.length, IPAddress, portNumber);

			socket.send(filePacket);

			byte[] receive = new byte[256];
			DatagramPacket receiveAck = new DatagramPacket(receive, receive.length);
			socket.receive(receiveAck);
			byte[] data = receiveAck.getData();
			ByteArrayInputStream in = new ByteArrayInputStream(data);
			ObjectInputStream is = new ObjectInputStream(in);
			Segment SegAck = null;
			try {
				SegAck = (Segment) is.readObject();

			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			System.out.println("Acknowledgement received: " + SegAck.getSq());
		}

	}







	/* TODO: This function is essentially the same as the sendFileNormal function
	 *      except that it resends data segments if no ACK for a segment is 
	 *      received from the server.*/
	public void sendFileWithTimeOut(int portNumber, InetAddress IPAddress, File file, float loss) throws IOException {
		socket = new DatagramSocket();
		int size = 4;
		FileInputStream fileInputStream = new FileInputStream(file);
		byte[] buffer = new byte[(int) file.length()];
		fileInputStream.read(buffer);

		List<byte[]> segments = new ArrayList<>();
		for (int i = 0; i < file.length();) {
			byte[] segment = new byte[(int) Math.min(size, file.length() - i)];
			for (int j = 0; j < segment.length; j++, i++) {
				segment[j] = buffer[i];
			}
			segments.add(segment);
		}
		System.out.println(segments);
		int sq = 0;
		int errorNum = 0;


		for (int i =0; i < segments.size(); i++) {
			String stringData = new String(segments.get(i));
			Segment fileSeg = new Segment();
			fileSeg.setSize(segments.get(i).length);
			fileSeg.setSq(sq);
			fileSeg.setType(SegmentType.Data);
			fileSeg.setPayLoad(stringData);
			fileSeg.setChecksum(checksum(stringData, isCorrupted(loss)));
			sq = 1 - sq;

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream os = new ObjectOutputStream(outputStream);
			os.writeObject(fileSeg);
			byte[] dataFile = outputStream.toByteArray();
			DatagramPacket filePacket = new DatagramPacket(dataFile, dataFile.length, IPAddress, portNumber);
			socket.send(filePacket);
			socket.setSoTimeout(100);

			while (true) {
				try {
					byte[] receive = new byte[256];
					DatagramPacket receiveAck = new DatagramPacket(receive, receive.length);
					socket.receive(receiveAck);
					byte[] data = receiveAck.getData();
					ByteArrayInputStream in = new ByteArrayInputStream(data);
					ObjectInputStream is = new ObjectInputStream(in);
					Segment SegAck = null;
					try {
						SegAck = (Segment) is.readObject();
						errorNum = 0;
						break;

					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
					System.out.println("Acknowledgement received: " + SegAck.getSq());

				} catch(Exception SocketTimeoutException){
					i--;
					errorNum++;
					System.out.println(errorNum	);
					if (errorNum == RETRY_LIMIT){
						System.out.println("Error limit reached, exiting... ");
						System.exit(0);
					}
					break;
				}
			}
		}
	}
	}


