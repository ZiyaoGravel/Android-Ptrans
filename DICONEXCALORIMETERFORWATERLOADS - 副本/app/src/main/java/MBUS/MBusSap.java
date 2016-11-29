package MBUS;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.TimeoutException;

import MBUS.MBusMessage.MessageType;
//import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;


/**
 * M-Bus Application Layer Service Access Point - Use this access point to communicate using the M-Bus wired protocol.
 * 
 * @author Stefan Feuerhahn
 * 
 */
public class MBusSap {

	//Listage des ports
	//private SerialPort serialPort;
	//static private CommPortIdentifier portId;

	
	// 261 is the maximum size of a long frame
	private final static int MAX_MESSAGE_SIZE = 261;

	private final SerialTransceiver serialTransceiver;

	private final byte[] outputBuffer = new byte[MAX_MESSAGE_SIZE];

	private final byte[] dataRecordsAsBytes = new byte[MAX_MESSAGE_SIZE];

	private final boolean[] frameCountBits;

	private DataOutputStream os = null;
	private DataInputStream is = null;

	private int timeout = 300;
	private SecondaryAddress secondaryAddress = null;

	/**
	 * Creates an M-Bus Service Access Point that is used to read meters.
	 * 
	 * @param serialPortName
	 *            examples for serial port identifiers are on Linux "/dev/ttyS0" or "/dev/ttyUSB0" and on Windows "COM1"
	 * @param baudRate
	 *            the baud rate to use.
	 */
	public MBusSap(String serialPortName, int baudRate) {
		serialTransceiver = new SerialTransceiver(serialPortName, baudRate, SerialPort.DATABITS_8,
				SerialPort.STOPBITS_1, SerialPort.PARITY_EVEN);
		frameCountBits = new boolean[254];
		for (int i = 0; i < frameCountBits.length; i++) {
			frameCountBits[i] = true;
		}
	}

	/**
	 * Opens the serial port. The serial port needs to be opened before attempting to read a device.
	 * 
	 * @throws IOException
	 *             if any kind of error occurs opening the serial port.
	 */
	public void open() throws IOException {
		serialTransceiver.open();
		os = serialTransceiver.getOutputStream();
		is = serialTransceiver.getInputStream();
	}

	/**
	 * Closes the serial port.
	 */
	public void close() {
		serialTransceiver.close();
	}

	/**
	 * Sets the maximum time in ms to wait for new data from the remote device.
	 * 
	 * @param timeout
	 *            the maximum time in ms to wait for new data. Must be greater then 0.
	 */
	public void setTimeout(int timeout) {
		if (timeout <= 0) {
			throw new IllegalArgumentException("timeout may not be 0");
		}
		this.timeout = timeout;
	}

	/**
	 * Returns the timeout in ms.
	 * 
	 * @return the timeout in ms.
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Reads a meter using primary addressing. Sends a data request (REQ_UD2) to the remote device and returns the
	 * variable data structure from the received RSP_UD frame.
	 * 
	 * @param primaryAddress
	 *            the primary address of the meter to read. For secondary address use 0xfd.
	 * @return the variable data structure from the received RSP_UD frame
	 * @throws IOException
	 *             if any kind of error (including timeout) occurs while trying to read the remote device. Note that the
	 *             connection is not closed when an IOException is thrown.
	 * @throws TimeoutException
	 *             if no response at all (not even a single byte) was received from the meter within the timeout span.
	 */
	public VariableDataStructure read(int primaryAddress) throws IOException, TimeoutException {

		if (serialTransceiver.isClosed() == true) {
			throw new IllegalStateException("Serial port is not open.");
		}

		if (frameCountBits[primaryAddress]) {
			sendShortMessage(primaryAddress, 0x7b);
			frameCountBits[primaryAddress] = false;
		}
		else {
			sendShortMessage(primaryAddress, 0x5b);
			frameCountBits[primaryAddress] = true;
		}

		MBusMessage mBusMessage = receiveMessage();

		if (mBusMessage.getMessageType() != MessageType.RSP_UD) {
			throw new IOException(
					"Received wrong kind of message. Expected RSP_UD but got: " + mBusMessage.getMessageType());
		}

		if (mBusMessage.getAddressField() != primaryAddress) {
//			throw new IOException("Received RSP_UD message with unexpected address field. Expected " + primaryAddress
//					+ " but received " + mBusMessage.getAddressField());
		}

		try {
			mBusMessage.getVariableDataResponse().decode();
		} catch (DecodingException e) {
			throw new IOException("Error decoding incoming RSP_UD message.", e);
		}

		return mBusMessage.getVariableDataResponse();

	}

	/**
	 * Writes to a meter using primary addressing. Sends a data send (SND_UD) to the remote device and returns a true if
	 * slave sends a 0x7e else false
	 * 
	 * @param primaryAddress
	 *            the primary address of the meter to write. For secondary address use 0xfd.
	 * @param data
	 *            the data to sends to the meter.
	 * @return true if data could be sent else false
	 * @throws IOException
	 *             if any kind of error (including timeout) occurs while trying to read the remote device. Note that the
	 *             connection is not closed when an IOException is thrown.
	 * @throws TimeoutException
	 *             if no response at all (not even a single byte) was received from the meter within the timeout span.
	 */
	public boolean write(int primaryAddress, byte[] data) throws IOException, TimeoutException {

		boolean ret;
		if (data == null) {
			data = new byte[] {};
		}
		ret = sendLongMessage(primaryAddress, 0x73, 0x51, data.length, data);
		MBusMessage mBusMessage = receiveMessage();

		if (mBusMessage.getMessageType() != MessageType.SINGLE_CHARACTER) {
			throw new IOException("unable to select component");
		}
		return ret;
	}

	/**
	 * [alpha]<br>
	 * Scans if any device response to the given wildcard.
	 * 
	 * @param wildcard
	 *            secondary address wildcard e.g. f1ffffffffffffff
	 * @return true ifany device responsed else false
	 */
	public boolean scanSelection(SecondaryAddress wildcard) {

		ByteBuffer bf = ByteBuffer.allocate(8);
		byte[] ba = new byte[8];

		bf.order(ByteOrder.LITTLE_ENDIAN);

		bf.put(wildcard.asByteArray());

		bf.position(0);
		bf.get(ba, 0, 8);

		boolean ret = false;
		try {
			if (sendLongMessage(0xfd, 0x53, 0x52, 8, ba)) {

				MBusMessage mBusMessage = receiveMessage();

				if (mBusMessage.getMessageType() == MessageType.SINGLE_CHARACTER) {
					ret = true;
				}
			}
		} catch (IOException e) {
			ret = true;
		} catch (TimeoutException e) {
			ret = false;
		}
		return ret;
	}

	/**
	 * Selects the meter with the specified secondary address. After this the meter can be read on primary address 0xfd.
	 * 
	 * @param secondaryAddress
	 *            the secondary address of the meter to select.
	 * @throws IOException
	 *             if any kind of error (including timeout) occurs while trying to read the remote device. Note that the
	 *             connection is not closed when an IOException is thrown.
	 * @throws TimeoutException
	 *             if no response at all (not even a single byte) was received from the meter within the timeout span.
	 */
	public void selectComponent(SecondaryAddress secondaryAddress) throws IOException, TimeoutException {
		this.secondaryAddress = secondaryAddress;
		componentSelection(false);
	}

	/**
	 * Deselects the previously selected meter.
	 * 
	 * @throws IOException
	 *             if any kind of error (including timeout) occurs while trying to read the remote device. Note that the
	 *             connection is not closed when an IOException is thrown.
	 * @throws TimeoutException
	 *             if no response at all (not even a single byte) was received from the meter within the timeout span.
	 */
	public void deselectComponent() throws IOException, TimeoutException {
		if (secondaryAddress != null) {
			componentSelection(true);
			secondaryAddress = null;
		}
	}

	public void selectForReadout(int primaryAddress, List<DataRecord> dataRecords)
			throws IOException, TimeoutException {

		int i = 0;
		for (DataRecord dataRecord : dataRecords) {
			i += dataRecord.encode(dataRecordsAsBytes, i);
		}
		sendLongMessage(primaryAddress, 0x53, 0x51, i, dataRecordsAsBytes);
		MBusMessage mBusMessage = receiveMessage();

		if (mBusMessage.getMessageType() != MessageType.SINGLE_CHARACTER) {
			throw new IOException("unable to select component");
		}
	}

	public void resetReadout(int primaryAddress) throws IOException, TimeoutException {
		sendLongMessage(primaryAddress, 0x53, 0x50, 0, new byte[] {});
		MBusMessage mBusMessage = receiveMessage();

		if (mBusMessage.getMessageType() != MessageType.SINGLE_CHARACTER) {
			throw new IOException("unable to select component");
		}
	}

	/**
	 * Sends a SND_NKE message to reset the FCB (frame counter bit).
	 * 
	 * @param primaryAddress
	 *            the primary address of the meter to reset.
	 * @throws IOException
	 *             if an error occurs during the reset process.
	 * @throws TimeoutException
	 *             if the slave does not answer with an 0xe5 message within the configured timeout span.
	 */
	public void linkReset(int primaryAddress) throws IOException, TimeoutException {
		sendShortMessage(primaryAddress, 0x40);
		MBusMessage mBusMessage = receiveMessage();

		if (mBusMessage.getMessageType() != MessageType.SINGLE_CHARACTER) {
			throw new IOException("unable to reset link");
		}

		frameCountBits[primaryAddress] = true;
	}

	private void componentSelection(boolean deselect) throws IOException, TimeoutException {
		ByteBuffer bf = ByteBuffer.allocate(8);
		byte[] ba = new byte[8];

		bf.order(ByteOrder.LITTLE_ENDIAN);

		bf.put(secondaryAddress.asByteArray());

		bf.position(0);
		bf.get(ba, 0, 8);

		// send select/deselect
		if (deselect) {
			sendLongMessage(0xfd, 0x53, 0x56, 8, ba);
		}
		else {
			sendLongMessage(0xfd, 0x53, 0x52, 8, ba);
		}

		MBusMessage mBusMessage = receiveMessage();

		if (mBusMessage.getMessageType() != MessageType.SINGLE_CHARACTER) {
			throw new IOException("unable to select component");
		}
	}

	private void sendShortMessage(int slaveAddr, int cmd) throws IOException {
		outputBuffer[0] = 0x10;
		outputBuffer[1] = (byte) (cmd);
		outputBuffer[2] = (byte) (slaveAddr);
		outputBuffer[3] = (byte) (cmd + slaveAddr);
		outputBuffer[4] = 0x16;
		os.write(outputBuffer, 0, 5);
	}

	private boolean sendLongMessage(int slaveAddr, int controlField, int ci, int length, byte[] data) {
		int i, j;
		int checksum = 0;

		outputBuffer[0] = 0x68;
		outputBuffer[1] = (byte) (length + 3);
		outputBuffer[2] = (byte) (length + 3);
		outputBuffer[3] = 0x68;
		outputBuffer[4] = (byte) controlField;
		outputBuffer[5] = (byte) slaveAddr;
		outputBuffer[6] = (byte) ci;

		for (i = 0; i < length; i++) {
			outputBuffer[7 + i] = data[i];
		}

		for (j = 4; j < (i + 7); j++) {
			checksum += outputBuffer[j];
		}

		outputBuffer[i + 7] = (byte) (checksum & 0xff);

		outputBuffer[i + 8] = 0x16;

		try {
			os.write(outputBuffer, 0, i + 9);
		} catch (IOException e) {
			return false;
		}

		return true;
	}

	private MBusMessage receiveMessage() throws IOException, TimeoutException {

		int timePassedTotal = 0;
		int numBytesReadTotal = 0;
		int messageLength = -1;

		byte[] inputBuffer = new byte[MAX_MESSAGE_SIZE];

		while (true) {
			if (is.available() > 0) {

				int numBytesRead = is.read(inputBuffer, numBytesReadTotal, MAX_MESSAGE_SIZE - numBytesReadTotal);

				numBytesReadTotal += numBytesRead;

				if (messageLength == -1) {

					if ((inputBuffer[0] & 0xff) == 0xe5) {
						messageLength = 1;
					}
					else if ((inputBuffer[0] & 0xff) == 0x68 && numBytesReadTotal > 1) {
						messageLength = (inputBuffer[1] & 0xff) + 6;
					}
				}

				if (numBytesReadTotal == messageLength) {
					break;
				}
			}

			if (timePassedTotal > timeout) {
				if (numBytesReadTotal == 0) {
					throw new TimeoutException();
				}
				if (numBytesReadTotal != messageLength) {
					throw new IOException("Incomplete response message received.");
				}
			}
			else {

				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}

				timePassedTotal += 100;
			}

		}

		MBusMessage mBusMessage;
		try {
			mBusMessage = new MBusMessage(inputBuffer, messageLength);
		} catch (DecodingException e) {
			throw new IOException("Error decoding incoming M-Bus message.");
		}

		return mBusMessage;

	}
	
	
	
	
	
	//Test !!!!!!!!!!!!!!
	public String[] ecrire(String nomFic, String texte)
	{
		//on va chercher le chemin et le nom du fichier et on me tout ca dans un String
		String adressedufichier = "/Users/Gouliarmis/Desktop/Ptrans_MBUS/test.txt";
		String flow=new String();
		String power=new String();	
		String tempH=new String();
		String tempC=new String();		
		String deltaT=new String();
		String power_corr=new String();
	
		//on met try si jamais il y a une exception
		try
		{
			/**
			 * BufferedWriter a besoin d un FileWriter, 
			 * les 2 vont ensemble, on donne comme argument le nom du fichier
			 * true signifie qu on ajoute dans le fichier (append), on ne marque pas par dessus 
			 
			 */
			File file = new File(adressedufichier);
			if (file.exists()) {
				FileWriter fw = new FileWriter(adressedufichier);
				// le BufferedWriter output auquel on donne comme argument le FileWriter fw cree juste au dessus
				BufferedWriter output = new BufferedWriter(fw);
				
				//on marque dans le fichier ou plutot dans le BufferedWriter qui sert comme un tampon(stream)
				output.write(texte);
				//on peut utiliser plusieurs fois methode write
				
				output.flush();
				//ensuite flush envoie dans le fichier, ne pas oublier cette methode pour le BufferedWriter
				
				output.close();
				//et on le ferme
				System.out.println("fichier cr��");
				
				
				TabFileReader.readTextFile(adressedufichier,' ',"");
				for(int i=0; i<TabFileReader.ncol();i++){
					//Affichage Flow
					if((TabFileReader.words[4][i]!=null) && (TabFileReader.words[4][i].equals("unit:CUBIC_METRE_PER_HOUR"))){	
							System.out.print(TabFileReader.words[4][i-4]);		
							System.out.print(TabFileReader.words[4][i-1]);
							System.out.println(TabFileReader.words[4][i]);
							
							//R�cup�ration de la valeur
							for(int j=0; j<TabFileReader.words[4][i-1].length(); j++){
								String mot=TabFileReader.words[4][i-1];
								int taille = TabFileReader.words[4][i-1].length();
								if(mot.charAt(j)==':'){
									flow=mot.substring(j+1,taille-1 ); 
									System.out.println(flow);
								}
							}
					}
					//Affichage Power
					if((TabFileReader.words[5][i]!=null) && (TabFileReader.words[5][i].equals("unit:WATT"))){	
						System.out.print(TabFileReader.words[5][i-3]);		
						System.out.print(TabFileReader.words[5][i-1]);
						System.out.println(TabFileReader.words[5][i]);
						
						//R�cup�ration de la valeur
						for(int j=0; j<TabFileReader.words[5][i-1].length(); j++){
							String mot=TabFileReader.words[5][i-1];
							int taille = TabFileReader.words[5][i-1].length();
							if(mot.charAt(j)==':'){
								power=mot.substring(j+1,taille-1 ); 
								System.out.println(power);
							}
						}
					}
					//Affichage Temp
					if((TabFileReader.words[6][i]!=null) && (TabFileReader.words[7][i].equals("unit:DEGREE_CELSIUS"))){	
						System.out.print(TabFileReader.words[7][i-4]);		
						System.out.print(TabFileReader.words[7][i-1]);
						System.out.println(TabFileReader.words[7][i]);
				
						//R�cup�ration de la valeur
						for(int j=0; j<TabFileReader.words[6][i-1].length(); j++){
							String mot=TabFileReader.words[6][i-1];
							int taille = TabFileReader.words[6][i-1].length();
							if(mot.charAt(j)==':'){
								tempH=mot.substring(j+1,taille-1); 
								System.out.println(tempH);
							}
						}
					}
					//Affichage TempC
					if((TabFileReader.words[7][i]!=null) && (TabFileReader.words[7][i].equals("unit:DEGREE_CELSIUS"))){	
						System.out.print(TabFileReader.words[7][i-4]);		
						System.out.print(TabFileReader.words[7][i-1]);
						System.out.println(TabFileReader.words[7][i]);
				
						//R�cup�ration de la valeur
						for(int j=0; j<TabFileReader.words[7][i-1].length(); j++){
							String mot=TabFileReader.words[7][i-1];
							int taille = TabFileReader.words[7][i-1].length();
							if(mot.charAt(j)==':'){
								tempC=mot.substring(j+1,taille-1); 
								System.out.println(tempC);
							}
						}
					}
					//Affichage Delta Temp
					if((TabFileReader.words[8][i]!=null) && (TabFileReader.words[8][i].equals("unit:KELVIN"))){	
						System.out.print(TabFileReader.words[8][i-4]);		
						System.out.print(TabFileReader.words[8][i-1]);
						System.out.println(TabFileReader.words[8][i]);

						//R�cup�ration de la valeur
						for(int j=0; j<TabFileReader.words[8][i-1].length(); j++){
							String mot=TabFileReader.words[8][i-1];
							int taille = TabFileReader.words[8][i-1].length();
							if(mot.charAt(j)==':'){
								deltaT=mot.substring(j+1,taille-1); 
								System.out.println(deltaT);
							}
						}
					}
				}
				
				power_corr = new String(ajustement(power, tempH));
			}
			else{
				System.out.println("Fichier introuvable");
			}
		}
		catch(IOException ioe){
			System.out.print("Erreur : ");
			ioe.printStackTrace();
		}
		
		String[] toPrint = new String[5];
		toPrint[0]=flow;
		toPrint[1]=power_corr;
		toPrint[2]=tempH;
		toPrint[3]=tempC;
		toPrint[4]=deltaT;
		
		return toPrint;
	}
	
	public String ajustement(String power, String tempH){
		double dpower_corr;
		float fpower=Float.parseFloat(power);
		float ftempH=Float.parseFloat(tempH);

		dpower_corr = fpower/((0.0735*ftempH)+5.0926);
		String power_corr = String.valueOf(dpower_corr);
		return power_corr;
	}
	

	public static void main(String args[])
	{
	
		//Listing des ports de la machine
		//listePortsDispo();
		
		System.out.println();
		System.out.println("-----------------------------------------------------------------");
		System.out.println();
		
		//Mise en place de la liaison s�rie sur COM2
		MBusSap mbusSap=new MBusSap("/dev/tty.usbserial-A3VPJR5B", 2400);
		
		//Ouverture du port COM2
		try{
			mbusSap.open();
		}catch(IOException e){
			System.out.println(e);
		}
		
		//Fixation d'un d�lai maximal pour la r�ception des donn�es
		mbusSap.setTimeout(20000);
		
		//R�cup�ration des donn�es
		VariableDataStructure varDatStruc;
		try{
			//Lecture des donn�es re�ues
			int primaryAddress = 0;
			varDatStruc = mbusSap.read(primaryAddress);
			System.out.println(varDatStruc);
			
			System.out.println();
			System.out.println("-----------------------------------------------------------------");
			System.out.println();
			
			//Conversion des donn�es re�ues en cha�ne de caract�re
			String data;
			data = varDatStruc.toString();
			
			//Ecriture des donn�es re�ues dans un fichier et r�cup�rations de celles-ci
			String[] toPrint = mbusSap.ecrire("/Users/Gouliarmis/Desktop/Ptrans_MBUS/test.txt", data);
			
			System.out.println("-----------------------------------------------------------------");

			//Affichage des donn�es r�cup�r�es
			for(int i=0; i<5; i++){
				System.out.println(toPrint[i]);
			}
		}catch(IOException e){
			System.out.println(e);
		}catch(TimeoutException te){
			System.out.println(te);
		}
		
		//Fermeture du port
		mbusSap.close();
		
	}//fin du main
}
