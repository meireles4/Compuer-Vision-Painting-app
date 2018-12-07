import java.awt.*;

import java.awt.event.*;
import java.awt.image.*;
import javax.imageio.*;
import javax.swing.JFrame;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import java.io.*;
import java.util.Arrays;

import org.opencv.photo.Photo;

// ---------------------------------------------------------------
// Classe que cria uma Frame principal, onde se situam os comandos
// de manipulação de imagem. Implementa a interface ActionListener
// para lidar com os eventos produzidos pelos botões.
// ---------------------------------------------------------------
class PSI extends Frame implements ActionListener {
	private static final long serialVersionUID = 1L;
	
	// Variáveis globais de apoio
	// Atenção: E se eu quiser ter múltiplas imagens?
	// Isto deve estar na classe ImageFrame!
	private Image image;
	private int sizex;
	private int sizey;;
	private int matrix[];
	ImagePanel imagePanel; 
	
	static boolean mudarCor = false;
	static boolean pintar = false;
	
	static Mat pintura;
	
	static double[] corRastreada = new double[] {0,0,0};
	static int mode = 0;
	static int raio = 20;
	
	// Função main cria uma instance dinâmica da classe
	public static void main(String args[]) throws InterruptedException
	{
		new PSI();
		
		//frame of output
		JFrame frame = new JFrame("Webcam Capture");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(600, 600);
		FacePanel facePanel = new FacePanel();
		frame.setContentPane(facePanel);
		
		//Place frame in the centre
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		frame.setLocation(dim.width/2-frame.getSize().width, dim.height/2-frame.getSize().height/2);
		
		//auxiliar frame
		JFrame auxframe = new JFrame("Auxiliar");
		auxframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		auxframe.setSize(600, 600);
		FacePanel auxfacePanel = new FacePanel();
		auxframe.setContentPane(auxfacePanel);
		
		//Place auxframe in the centre
		Dimension auxdim = Toolkit.getDefaultToolkit().getScreenSize();
		auxframe.setLocation(auxdim.width/2, auxdim.height/2-auxframe.getSize().height/2);
		
		MatToBufImg matToBufferedImageConverter = new MatToBufImg();
		
		//Load the native library
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		
		VideoCapture webCam = new VideoCapture(0);
		if(!webCam.isOpened()) {
			System.out.println("!!! Did not connect to camera !!!");
		}
		else 
			System.out.println("Found webcam: " + webCam.toString());
		
		frame.setVisible(true);
		//auxframe.setVisible(true);
		
		Mat webcam_image = new Mat();
		
		//Open and Read from the video stream
		if( webCam.isOpened()) {
			
			Thread.sleep(200);  //This one-time delay allows the WebCam to initialise itself
			
			//first read to calculate colour
			webCam.read(webcam_image);
			
			//get number of rows and columns of the image
			int matRows = webcam_image.rows();
			int matCols = webcam_image.cols();
			
			double[] corPincel = null;
			int[] posicaoPincel = new int[] {0,0};
			
			pintura = webcam_image.clone();
			pintura.setTo(new Scalar(255,255,255));
				
			while ( true ) {
				if( !webcam_image.empty() ) {
					
					Thread.sleep(20); //This delay eases the computational load with little performance leakage
					
					//capture images from the webcam
					webCam.read(webcam_image);
					Core.flip(webcam_image, webcam_image, +1);
					
					//Se for pressionado o botao mudar cor
					if(mudarCor) {
						pintar = false;
						corPincel = calcularNovaCor(webcam_image);
						mudarCor = false;
					}
					
					if(pintar) {
						posicaoPincel = segmentAndFindPosition(webcam_image, corPincel, posicaoPincel);
						pintura = updatePintura(posicaoPincel, corPincel, webcam_image);
						webcam_image = drawPintura(webcam_image, pintura);
						webcam_image = drawCursor(posicaoPincel, webcam_image);
					}
					else
						webcam_image = drawPintura(webcam_image, pintura);
				
					//Update frame of modified image
					matToBufferedImageConverter.setMatrix(webcam_image, ".jpg");
					BufferedImage bufImg = matToBufferedImageConverter.getBufferedImage(); //get the JPG version of the mat
					
					facePanel.setFace(bufImg); //add the JPG image to the JPanel
					facePanel.repaint();       // and ask the system to repaint the (updated GUI)
					
				}
				else {
					System.out.println("!!! Nothing captured from webcam !!!");
					break;
				}
			} //end while
			webCam.release();
		}
		//release the webcam back to the OS
	}

	//Metodo que sobrepoe a matriz com as cores pintadas a imagem recolhida
	private static Mat drawPintura(Mat webcam_image, Mat pintura) {
		
		for(int i = 0; i < webcam_image.rows(); i++)
			for(int j = 0; j < webcam_image.cols(); j++) {
				double color[] = pintura.get(i, j);
				if(color[0] != 255 && color[1] != 255 && color[2] != 255) {
					webcam_image.put(i, j, new double[]{color[2], color[1], color[0]});
				}
			}
		
		return webcam_image;
	}

	//metodo que faz update à matriz que contem os pixeis pintados.
	//Com base no raio do circulo que se quer pintar calcula-se a distancia entre o centro do pincel e os pixeis
	//e caso seja menos que o raio a matriz é alterada conforme o modo ativo
	private static Mat updatePintura(int[] posicaoPincel, double[] corPincel, Mat webcam_image) {
		
		double[] white = new double[] {255,255,255};
		
		for(int i = -raio; i < raio; i++)
			for(int j = -raio; j < raio; j++) {
				
				if(posicaoPincel[0]+i >= 0 && posicaoPincel[0]+i < webcam_image.rows() 
						&& posicaoPincel[1]+j >= 0 && posicaoPincel[1]+j < webcam_image.cols()) {
					
					double dist = Math.sqrt(Math.pow(posicaoPincel[0]-posicaoPincel[0]+i, 2)+
							Math.pow(posicaoPincel[1]-posicaoPincel[1]+j, 2));
					
					if(dist < raio) {
						
						if(mode == 0)
							pintura.put(posicaoPincel[0]+i, posicaoPincel[1]+j, corPincel);
						
						else {
							double[] corPintura = pintura.get(posicaoPincel[0]+i, posicaoPincel[1]+j);		
							
							if( !Arrays.equals(white, corPintura)) {
								
								double newR = (corPintura[0] + corPincel[0]) / 2;
								double newG = (corPintura[1] + corPincel[1]) / 2;
								double newB = (corPintura[2] + corPincel[2]) / 2;
								double[] newColor = new double[] {newR, newG, newB};
								pintura.put(posicaoPincel[0]+i, posicaoPincel[1]+j, newColor);
							}
							else
								pintura.put(posicaoPincel[0]+i, posicaoPincel[1]+j, corPincel);
						}
					}
				}
				
			}
			
		return pintura;
	}

	//pinta uma "mira" preta tendo como centro a posicao do pincel
	private static Mat drawCursor(int[] posicaoPincel, Mat webcam_image) {
		
		int size = 50;
		
		for(int i = -size; i < size; i++) {
			if(posicaoPincel[0]+i > 0 && posicaoPincel[0]+i < webcam_image.rows())
				webcam_image.put(posicaoPincel[0]+i, posicaoPincel[1], new double[] {0,0,0});
		}
		for(int i = -size; i < size; i++) {
			if(posicaoPincel[1]+i > 0 && posicaoPincel[1]+i < webcam_image.cols())
				webcam_image.put(posicaoPincel[0], posicaoPincel[1]+i, new double[] {0,0,0});
		}
		return webcam_image;
	}

	
	private static int[] segmentAndFindPosition(Mat webcam_image, double[] corPincel, int[] posicaoPincel2) {
		
		int[] posicaoPincel = null;
		int[][] painted = new int[webcam_image.rows()][webcam_image.cols()];
		int[][] scores = new int[webcam_image.rows()][webcam_image.cols()];
		int range = 30;
		
		double[] color;
		double red, green, blue;
		
		//apply threshold
		for(int i = 0; i<webcam_image.rows(); i++)
			for(int j = 0; j < webcam_image.cols(); j++) {
				color = webcam_image.get(i, j);
				
				red = color[2];
				green = color[1];
				blue = color[0];
				
				if(red > corRastreada[0]-range && red < corRastreada[0]+range &&
						green > corRastreada[1]-range && green < corRastreada[1]+range &&
						blue > corRastreada[2]-range && blue < corRastreada[2]+range) {
					painted[i][j] = 1;
				}
				else
					painted[i][j] = 0;
			}
		
		
		//calculo da relevancia de cada pixel
		int sum;
		int maxscore = 0;
		for(int i = 0; i < webcam_image.rows(); i++)
			for(int j = 0; j < webcam_image.cols(); j++) {
				
				for(int k = -1; k < 2; k++)
					for(int w = -1; w < 2; w++) {
						if(i+k >= 0 && i+k <= webcam_image.rows() && j+w >= 0 && j+w <= webcam_image.cols()) {
							scores[i][j] += painted[i][j];
							if(scores[i][j] > maxscore)
								maxscore = scores[i][j];
						}
					}
			}
		
		//calcular media das posicoes com maior pontuacao
		int sumR=0, sumG=0, sumB=0;
		double[] auxcolor;
		int sum_i=0, sum_j=0, count=0;
		for(int i = 0; i < webcam_image.rows(); i++)
			for(int j = 0; j < webcam_image.cols(); j++) {
				if(scores[i][j] == maxscore && scores[i][j] >= 8) {
					
					auxcolor = webcam_image.get(i, j);
					sumR += auxcolor[2];
					sumG += auxcolor[1];
					sumB += auxcolor[0];
					
					sum_i += i;
					sum_j += j;
					count++;
				}	
			}
		
		if(count > 0) {
			
			raio = (int) (0.25 * Math.sqrt(count));
			
			if(raio < 10)
				raio = 10;
			else if(raio > 100)
				raio = 100;
			
			int rangeDinamico = 100;
			double newR = sumR/count;
			double newG = sumG/count;
			double newB = sumB/count;
			
			if(Math.abs(newR - corPincel[0]) > rangeDinamico)
				newR = corRastreada[0];
			
			if(Math.abs(newG - corPincel[1]) > rangeDinamico)
				newG = corRastreada[1];
			
			if(Math.abs(newB - corPincel[2]) > rangeDinamico)
				newB = corRastreada[2];
			
			double[] newcorRastreada = new double[] {newR, newG, newB};
			corRastreada = newcorRastreada;
			return new int[]{sum_i/count,sum_j/count};
		}
		else {
			return new int[] {posicaoPincel2[0], posicaoPincel2[1]};
		}
	}

	// Construtor
	public PSI()
	{
		// Lidar com o evento de Fechar Janela
		addWindowListener(new WindowAdapter() {
      		public void windowClosing(WindowEvent e) {
        		System.exit(0);
      		}
		});

		// Sinalizar que não existe nenhum ImagePanel
		imagePanel = null;
		
		// Criar botões 
		this.setLayout(new GridLayout(5,1,1,1));
		
		Button button = new Button("Change Color");
		button.setVisible(true);
		button.addActionListener(this);
		add(button);
		
		button = new Button("Continue");
		button.setVisible(true);
		button.addActionListener(this);
		add(button);
		
		button = new Button("Stop");
		button.setVisible(true);
		button.addActionListener(this);
		add(button);
		
		button = new Button("Reset");
		button.setVisible(true);
		button.addActionListener(this);
		add(button);
		
		button = new Button("Change Mode");
		button.setVisible(true);
		button.addActionListener(this);
		add(button);
		
		pack();
		
		// Janela principal 	
		setLocation(100,100);
		setSize(100,300);
		setVisible(true);
	}
	
	
	// O utilizador carregou num botão
	public void actionPerformed (ActionEvent myEvent)
	{
		// Qual o botão premido?
		Button pressedButton = (Button)myEvent.getSource();
		String nomeBotao = pressedButton.getActionCommand();
		
		if (nomeBotao.equals("Change Color")) mudarCor();
		else if (nomeBotao.equals("Continue")) continuar();
		else if (nomeBotao.equals("Stop")) parar();
		else if (nomeBotao.equals("Reset")) reset();
		else if (nomeBotao.equals("Change Mode")) changeMode();
			
	}
	
	private void changeMode() {
		if(mode == 0)
			mode = 1;
		else
			mode = 0;
		
	}

	private void reset() {
		pintura.setTo(new Scalar(255,255,255));
	}

	private void parar() {
		pintar = false;
	}

	private void mudarCor() {
		mudarCor = true;
	}
	
	private void continuar() {
		pintar = true;
	}
	
	private static double[] calcularNovaCor(Mat webcam_image) {
		
		long pixelCounter = 0;
		long redBucket = 0;
		long greenBucket = 0;
		long blueBucket = 0;
		
		double[] corPixel;
		
		for(int i = 0; i < webcam_image.rows(); i++)
			for(int j = 0; j < webcam_image.cols(); j++) {
				corPixel = webcam_image.get(i, j);
				
				pixelCounter++;
				redBucket += corPixel[2];
				greenBucket += corPixel[1];
				blueBucket += corPixel[0];
			}
		
		double[] novaCor = {redBucket/pixelCounter, 
				greenBucket/pixelCounter, 
				blueBucket/pixelCounter};
		
		corRastreada = novaCor;
		return novaCor;
	}

}
