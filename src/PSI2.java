import java.awt.Button;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;

import javax.swing.JFrame;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.videoio.VideoCapture;

class PSI2 extends Frame implements ActionListener {
	private static final long serialVersionUID = 1L;
	
	static boolean mudarCor = false;
	static boolean pintar = false;
	
	static Mat pintura;
	
	static double[] corRastreada = new double[] {0,0,0};
	static int mode = 0;
	
	// Função main cria uma instance dinâmica da classe
	public static void main(String args[]) throws InterruptedException
	{
		new PSI2();
		
		//frame of output
		JFrame frame = new JFrame("Webcam Capture");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(700, 600);
		FacePanel facePanel = new FacePanel();
		frame.setContentPane(facePanel);
		
		//Place frame in the centre
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		frame.setLocation(dim.width/2-frame.getSize().width, dim.height/2-frame.getSize().height/2);
		
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
		
		Mat webcam_image = new Mat();
		
		//Open and Read from the video stream
		if( webCam.isOpened()) {
			
			Thread.sleep(200);  //This one-time delay allows the WebCam to initialise itself
			
			webCam.read(webcam_image);
			
			double[] corPincel = null;
			
			int[][] morphOpenning = null;
			int[][] connectCompon = null;
			
			pintura = webcam_image.clone();
			pintura.setTo(new Scalar(255,255,255));
			
			int[][] changed = null;
				
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
						changed = new int[webcam_image.rows()][webcam_image.cols()];
						mudarCor = false;
					}
					
					if(pintar) {
						morphOpenning = morphOpenning(webcam_image, corPincel);
						connectCompon = conectedComponents(morphOpenning);
						calcCorRastreada(connectCompon, webcam_image, corPincel);
						
						//Este metodo serve para aplicar o resultado do morphOpenning e cca a imagem
						//webcam_image = applyMorphOpenning(webcam_image, connectCompon, corPincel); 
						
						updatePintura(connectCompon, corPincel, webcam_image, changed);
						changed = updateChanged(changed, connectCompon);
					}
					
					webcam_image = drawPintura(webcam_image, connectCompon);
				
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

	private static int[][] updateChanged(int[][] changed, int[][] connectCompon) {
		
		for(int i = 0; i < connectCompon.length; i++)
			for(int j = 0; j < connectCompon[0].length; j++) {
				if(connectCompon[i][j] == 1 && changed[i][j] != 1)
					changed[i][j] = 1;
			}
		
		return changed;
	}

	private static int[][] conectedComponents(int[][] morphOpenning) {
		
		int label = 0;
		int[][] connected = new int[morphOpenning.length][morphOpenning[0].length];
		HashMap<Integer, Integer> ligacoes = new HashMap<>();
		
		for(int i = 0; i < morphOpenning.length; i++) {
			for(int j = 0; j < morphOpenning[0].length ; j++) {
				
				if(morphOpenning[i][j] == 1) {
					if(i == 0 && j == 0) { //primeira celula da matriz
						label++;
						connected[i][j] = label;
						ligacoes.put(label, label);
					}
					else if( i == 0) { //primeira linha da matriz
						if(connected[i][j-1] != 0) {
							connected[i][j] = label;
						}
						else if(connected[i][j-1] == 0) {
							label++;
							connected[i][j] = label;
							ligacoes.put(label, label);
						}
					}
					else if(j == 0) { //primeira coluna da matriz
						if(connected[i-1][j] != 0) {
							connected[i][j] = connected[i-1][j];
						}
						else if(connected[i-1][j] == 0) {
							label++;
							connected[i][j] = label;
							ligacoes.put(label, label);
						}
					}
					else {
						if(connected[i-1][j] != 0 && connected[i][j-1] != 0) { //o de cima e o de tras sao defirentes de zero
							if(connected[i-1][j] < connected[i][j-1] ) {
								connected[i][j] = connected[i-1][j];
								ligacoes.put(connected[i][j-1], connected[i-1][j]);
							}
							else if (connected[i][j-1] < connected[i-1][j] ) {
								connected[i][j] = connected[i][j-1];
								ligacoes.put(connected[i-1][j], connected[i][j-1]);
							}
							else
								connected[i][j] = connected[i][j-1];
						}
						else if(connected[i-1][j] != 0 || connected[i][j-1] != 0) { //ou o de cima ou o de tras e zero
							if(connected[i-1][j] != 0 )
								connected[i][j] = connected[i-1][j];
							else
								connected[i][j] = connected[i][j-1];
						}
						else { //o de cima e o de tras sao zero
							label++;
							connected[i][j] = label;
							ligacoes.put(label, label);
						}
					}
						
				}	
			}
		}//fim do ciclo de criar os componentes
		
		//atualizar a matriz conforme as ligacoes geradas
		for(int i = 0; i < morphOpenning.length; i++) {
			for(int j = 0; j < morphOpenning[0].length ; j++) {
				if(connected[i][j] != 0) {
					connected[i][j] = encontrarLigacao(ligacoes.get(connected[i][j]), ligacoes);
				}
			}
		}
		
		
		if(label > 0) {
			//guardar numero de pixeis de cada elemento conexo
			int[] countLabel = new int[label];
			
			for(int i = 0; i < connected.length; i++) {
				for(int j = 0; j < connected[0].length ; j++) {
				
					if(connected[i][j] != 0) {
						countLabel[connected[i][j]-1]++;
					}
					
				}	
			}
			
			//encontrar elemento conexo com mais pixeis que e o que nos interessa
			int max = -1;
			int index = 0;
			
			for(int i = 0; i < countLabel.length; i++) {
				if(countLabel[i] > max) {
					max = countLabel[i];
					index = i+1;
				}
			}
			
			//percorrer matriz dos componentes conexos para retirar todos os componentes e ficar so com o de maior dimensao
			for(int i = 0; i < connected.length; i++) {
				for(int j = 0; j < connected[0].length ; j++) {
					
					if(connected[i][j] != index)
						connected[i][j] = 0;
					
				}
			}
		}
		
		return connected;
	}

	private static int encontrarLigacao(Integer k, HashMap<Integer, Integer> ligacoes) {
		
		if(k == ligacoes.get(k))
			return k;
		else {
			return encontrarLigacao(ligacoes.get(k), ligacoes); 
		}
	}

	private static void calcCorRastreada(int[][] morphOpenning, Mat webcam_image, double[] corPincel) {
		
		int redCount = 0, greenCount = 0, blueCount = 0, count = 0;
		double[] novaCor;
		double[] colour = null;
		int maxDistancia = 50;
		
		for(int i = 0; i < webcam_image.rows(); i++) {
			for(int j = 0; j < webcam_image.cols(); j++) {
				if(morphOpenning[i][j] == 1) {
					colour = webcam_image.get(i, j);
					redCount += colour[2];
					greenCount += colour[1];
					blueCount += colour[0];
					count++;
				}
			}
		}
		
		if(count > 10) {
			novaCor = new double[] {redCount/count, greenCount/count, blueCount/count};

			if(Math.abs(novaCor[0] - corPincel[0]) > maxDistancia)
				novaCor[0] = corRastreada[0];
			
			if(Math.abs(novaCor[1] - corPincel[1]) > maxDistancia)
				novaCor[1] = corRastreada[1];
			
			if(Math.abs(novaCor[2] - corPincel[2]) > maxDistancia)
				novaCor[2] = corRastreada[2];
			
			corRastreada = novaCor;
		}
		
	}

	private static Mat applyMorphOpenning(Mat webcam_image, int[][] morphOpenning, double[] corPincel) {
		
		double[] corAux = new double[] {255,255,255};	
		
		for(int i = 0; i < webcam_image.rows(); i++) {
			for(int j = 0; j < webcam_image.cols(); j++) {
				if(morphOpenning[i][j] == 1) {
					webcam_image.put(i, j, corAux);
				}
			}
		}
		
		return webcam_image;
	}

	//Metodo que sobrepoe a matriz com as cores pintadas a imagem capturada pela camara
	private static Mat drawPintura(Mat webcam_image, int[][] connectCompon) {
		
		for(int i = 0; i < webcam_image.rows(); i++)
			for(int j = 0; j < webcam_image.cols(); j++) {
				double color[] = pintura.get(i, j);
				if(color[0] != 255 && color[1] != 255 && color[2] != 255) {
					
					if(connectCompon[i][j] == 1) {
						if(i == 0 || j == 0 || i == webcam_image.rows()-1 || j == webcam_image.cols()-1 ) {
							webcam_image.put(i, j, new double[]{0,0,0});
						}
						else if(connectCompon[i-1][j] == 0 || connectCompon[i+1][j] == 0 || connectCompon[i][j-1] == 0 || connectCompon[i][j+1] == 0) {
							webcam_image.put(i, j, new double[]{0,0,0});
						}
						else
							webcam_image.put(i, j, new double[]{color[2], color[1], color[0]});
					}
					else
						webcam_image.put(i, j, new double[]{color[2], color[1], color[0]});
				}
			}
		
		return webcam_image;
	}

	//metodo que faz update à matriz que contem os pixeis pintados.
	//Com base no raio do circulo que se quer pintar calcula-se a distancia entre o centro do pincel e os pixeis
	//e caso seja menos que o raio a matriz é alterada conforme o modo ativo
	private static void updatePintura(int[][] connectCompon, double[] corPincel, Mat webcam_image, int[][] changed) {
		
		double[] white = new double[] {255,255,255};
		
		for(int i = 0; i < connectCompon.length; i++) {
			for(int j = 0; j < connectCompon[0].length; j++) {
				
				if(connectCompon[i][j] == 1 && changed[i][j] != 1) {
					
					if(mode == 1) {
						double[] corPintura = pintura.get(i, j);		
						
						if( !Arrays.equals(white, corPintura)) {
							
							double newR = (corPintura[0] + corPincel[0]) / 2;
							double newG = (corPintura[1] + corPincel[1]) / 2;
							double newB = (corPintura[2] + corPincel[2]) / 2;
							double[] newColor = new double[] {newR, newG, newB};
							pintura.put(i, j, newColor);
							
						}
						else
							pintura.put(i, j, corPincel);
					}
					else
						pintura.put(i, j, corPincel);
				}
			}
		}
		
		/*
		for(int i = -raio; i < raio; i++)
			for(int j = -raio; j < raio; j++) {
				
				if(connectCompon[0]+i >= 0 && connectCompon[0]+i < webcam_image.rows() 
						&& connectCompon[1]+j >= 0 && connectCompon[1]+j < webcam_image.cols()) {
					
					if(dist < raio) {
						
						if(mode == 0)
							pintura.put(connectCompon[0]+i, connectCompon[1]+j, corPincel);
						
						else {
							double[] corPintura = pintura.get(connectCompon[0]+i, connectCompon[1]+j);		
							
							if( !Arrays.equals(white, corPintura)) {
								
								double newR = (corPintura[0] + corPincel[0]) / 2;
								double newG = (corPintura[1] + corPincel[1]) / 2;
								double newB = (corPintura[2] + corPincel[2]) / 2;
								double[] newColor = new double[] {newR, newG, newB};
								pintura.put(connectCompon[0]+i, connectCompon[1]+j, newColor);
							}
							else
								pintura.put(connectCompon[0]+i, connectCompon[1]+j, corPincel);
						}
					}
				}
				
			}
		*/
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

	//aplicar morphological openning e atualizar cor rastreada
	private static int[][] morphOpenning(Mat webcam_image, double[] corPincel) {
		
		int[][] painted = new int[webcam_image.rows()][webcam_image.cols()];
		
		int range = 50;
		
		double[] color;
		double red, green, blue;
		
		//apply threshold
		for(int i = 0; i<webcam_image.rows(); i++) {
			for(int j = 0; j < webcam_image.cols(); j++) {
				color = webcam_image.get(i, j);
				
				red = color[2];
				green = color[1];
				blue = color[0];
				
				if(red > corRastreada[0]-range && red < corRastreada[0]+range &&
						green > corRastreada[1]-100 && green < corRastreada[1]+100 &&
						blue > corRastreada[2]-range && blue < corRastreada[2]+range) {
					painted[i][j] = 1;
				}
				else
					painted[i][j] = 0;
			}
		}//end threshold
		
		
		//aplicar morphological openning (erosao + dilatacao)
		
		//aplicar erosao
		int[][] erosao = new int[webcam_image.rows()][webcam_image.cols()];
		for(int i = 1; i<webcam_image.rows()-1; i++) {
			for(int j = 1; j < webcam_image.cols()-1; j++) {
				
				if(painted[i-1][j] == 1 && painted[i][j+1] == 1 && painted[i+1][j] == 1 && painted[i][j-1] == 1 && painted[i][j] == 1) {
					erosao[i][j] = 1;
				}
			}
		}
		
		//aplicar dilatacao
		int[][] dilatacao = new int[webcam_image.rows()][webcam_image.cols()];
		for(int i = 1; i<webcam_image.rows()-1; i++) {
			for(int j = 1; j < webcam_image.cols()-1; j++) {
				
				if(erosao[i][j] == 1) {
					dilatacao[i-1][j] = 1;
					dilatacao[i][j+1] = 1;
					dilatacao[i+1][j] = 1;
					dilatacao[i][j-1] = 1;
					dilatacao[i][j] = 1;
				}
			}
		}
		
		return dilatacao;
			
	}

	// Construtor
	public PSI2()
	{
		// Lidar com o evento de Fechar Janela
		addWindowListener(new WindowAdapter() {
      		public void windowClosing(WindowEvent e) {
        		System.exit(0);
      		}
		});
		
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