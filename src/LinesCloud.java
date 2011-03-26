import processing.core.*;
import processing.opengl.*;

import codeanticode.glgraphics.*;

import org.openkinect.*;
import org.openkinect.processing.*;

import toxi.math.*;
import toxi.geom.*;

import controlP5.*;



public class LinesCloud extends PApplet {
	
	static final int GRID_WIDTH			= 640;
	static final int GRID_HEIGHT 		= 480;
	static final int GRID_ROWS 			= 80;
	static final int GRID_COLUMNS 		= 35;
	
	static final float EASING			= 0.075f;
	
	
	Kinect kinect;
	
	ControlP5 controlP5;
	Slider distanceThresholdSlider;
	Slider kinectTiltSlider;
	Slider cameraDistanceSlider;
	Slider2D cameraRotationSlider;
	
	PMatrix3D currCameraMatrix;
	PGraphics3D g3;
	
	float gridCenterX, gridCenterY;
	float gridSpaceX, gridSpaceY;
	float distanceToCenter;
	float distanceThreshold;
	
	float rot = 0;
	
	GLModel lines;
	
	int totallines;
	
	Vec3D[] linesPos = new Vec3D[GRID_WIDTH * GRID_HEIGHT];
	
	float[] depthLookUp = new float[2048];
	
	boolean saveFrames = false;
	
	
	
	
	public void setup() {
		size( 1280, 768, GLConstants.GLGRAPHICS );
		
		// Setup kinect
		kinect = new Kinect(this);
		kinect.start();
		kinect.enableDepth(true);
		kinect.processDepthImage(false);
		
		g3 = (PGraphics3D)g;
		
		initGui();
		
		
		// Get depth lookup table
		for ( int i = 0; i < depthLookUp.length; i++ ) depthLookUp[i] = rawDepthToCentimeters( i );
		
		
		// Initialize lines mesh
		gridSpaceX = GRID_WIDTH / GRID_ROWS;
		gridSpaceY = GRID_HEIGHT / GRID_COLUMNS;
		
		gridCenterX = ((GRID_ROWS - 1) * gridSpaceX) / 2;
		gridCenterY = ((GRID_COLUMNS - 1) * gridSpaceY) / 2;
		
		for (int i = 0; i < GRID_ROWS; i++ ) {
			for (int j = 0; j < GRID_COLUMNS; j++) {
				int offset = i + j * GRID_ROWS;
				float x = i * gridSpaceX;
				float y = j * gridSpaceY;
				float z = 0.0f;
		      	Vec3D pv = new Vec3D(x, y, z);
		      	linesPos[offset] = pv;
		    }
		}
		
		totallines = GRID_ROWS * GRID_COLUMNS;
		
		// Lines GLModel
		lines = new GLModel(this, totallines, LINES, GLModel.DYNAMIC);

		lines.initColors();
		lines.beginUpdateColors();
		for ( int i = 0; i < totallines; i++ ) lines.updateColor( i, 255, 255, 225 );
		lines.endUpdateColors(); 

		lines.beginUpdateVertices();
		for ( int i = 0; i < totallines; i++ ) lines.updateVertex( i, linesPos[i].x, linesPos[i].y, linesPos[i].z);
		lines.endUpdateVertices();   
		lines.setLineWidth( 2.0f );
		
		int totalIndices = GRID_ROWS * 2 * GRID_COLUMNS;  
		int indices[] = new int[totalIndices];
		  
		int n = 0;
		for ( int col = 0; col < GRID_COLUMNS; col++ ) {
			for ( int row = 0; row < GRID_ROWS - 1; row++ ) {
				int offset = col * GRID_ROWS;
				int n0 = offset + row;
				int n1 = offset + row + 1;
				indices[n++] = n0;
				indices[n++] = n1;
			}
		}
		  
		lines.initIndices( totalIndices );
		lines.updateIndices( indices );
		
	}
	
	
	
	public void draw() {
		int[] depth = kinect.getRawDepth();
		
		GLGraphics renderer = (GLGraphics)g;
	    renderer.beginGL(); 
	    
		background( 0 );
		
		hint( ENABLE_DEPTH_TEST );
		
		beginCamera();
		camera( 0.0f, 0.0f, cameraDistanceSlider.value(),
				0.0f, 0.0f, 0.0f, 
			    0.0f, 1.0f, 0.0f );
		rotateX( radians(45 - cameraRotationSlider.arrayValue()[1]) );
		rotateY( radians(45 - cameraRotationSlider.arrayValue()[0]) );
		
		// May add this to the gui as a toggle control
		/*rot += 0.0075f;
		rotateY( rot );
		rotateX( sin( frameCount * 0.01f ) * 0.25f );*/
		
		
		// Update lines vertices position in 'z'
		lines.beginUpdateVertices();
		for ( int i = 0; i < totallines; i++ ) {
			float distance = getDistanceAt( (int)linesPos[i].x, (int)linesPos[i].y, depth ) * 2.5f;
			if ( distance > distanceThreshold || distance == 0 ) distance = distanceThreshold;
			linesPos[i].z += (distance - linesPos[i].z) * EASING;
			lines.updateVertex( i, linesPos[i].x, linesPos[i].y, linesPos[i].z );
		}
		lines.endUpdateVertices();
		
		// Update lines colors
		lines.beginUpdateColors();
		for ( int i = 0; i < totallines; i++ ) {		
			float dt = linesPos[i].z / distanceThreshold;
			
			final float c0r = 255;
			final float c0g = 0;
			final float c0b = 0;
			
			final float c1r = 165;
			final float c1g = 255;
			final float c1b = 0;
			
			final float c2r = 0;
			final float c2g = 165;
			final float c2b = 255;
			
			float ctr = (dt < 0.5f) ? c0r * dt * 2 + ( c1r * (0.5f - dt) * 2 ) : c1r * (dt - 0.5f) * 2 + ( c2r * (1 - dt) * 2 );
			float ctb = (dt < 0.5f) ? c0b * dt * 2 + ( c1b * (0.5f - dt) * 2 ) : c1b * (dt - 0.5f) * 2 + ( c2b * (1 - dt) * 2 );
			float ctg = (dt < 0.5f) ? c0g * dt * 2 + ( c1g * (0.5f - dt) * 2 ) : c1g * (dt - 0.5f) * 2 + ( c2g * (1 - dt) * 2 );
			
			lines.updateColor( i, ctr, ctg, ctb );
		}
		lines.endUpdateColors();
		
		
		pushMatrix();
		translate( -gridCenterX, -gridCenterY, -distanceToCenter );
		renderer.model( lines );
		popMatrix();
		
		endCamera();
		
		renderer.endGL();
		
		hint( DISABLE_DEPTH_TEST );
		
		//stats();
		drawGui();
		
		
		// Save frames for movie
		if ( saveFrames == true ) saveFrame( "line-####.jpg" );
			
	}
	
	
	
	public void stats() {
		fill( 255 );
		textMode( SCREEN );
		String info  = "Kinect FPS: " + (int)kinect.getDepthFPS() + "\n";
			   info += "Processing FPS: " + (int)frameRate + "\n";
			   info += "Number of lines: " + totallines + "\n";
		text( info, 10, 16 );
	}
	
	
	public void initGui() {
		controlP5 = new ControlP5( this );
		controlP5.setAutoDraw( false );
		distanceThresholdSlider = controlP5.addSlider( "distance threshold", 0, 800 );
		distanceThresholdSlider.setValue( 400 );
		distanceThresholdSlider.setPosition( 10, 70 );
		
		kinectTiltSlider = controlP5.addSlider( "kinect tilt", -30, 30 );
		kinectTiltSlider.setValue( 0 );
		kinectTiltSlider.setPosition( 10, 90 );
		
		cameraDistanceSlider = controlP5.addSlider( "camera distance", -800, 800 );
		cameraDistanceSlider.setValue( -600 );
		cameraDistanceSlider.setPosition( 10, 110 );
		
		cameraRotationSlider = controlP5.addSlider2D( "camera rotation", 0, 0, 90, 90 );
		float[] slider2dInitVals = { 45, 45 };
		cameraRotationSlider.setArrayValue( slider2dInitVals );
		cameraRotationSlider.setPosition( 10, 130 );
		
		controlP5.addToggle( "saveFrames", false, 10, 250, 10, 10 );
		
		distanceToCenter 	= 200;
		distanceThreshold 	= distanceThresholdSlider.value();
		
	}
	
	public void drawGui() {
		currCameraMatrix = new PMatrix3D( g3.camera );
		
		camera();
		controlP5.draw();
		
		g3.camera 			= currCameraMatrix;
		distanceThreshold 	= distanceThresholdSlider.value();
		kinect.tilt( kinectTiltSlider.value() );
	}
	
	
	
	public float getDistanceAt( int x, int y, int[] depth ) {
		int offset = x + y * GRID_WIDTH;
		int rawDepth = depth[(int)offset];
		return depthLookUp[rawDepth];
	}
	
	
	public float rawDepthToCentimeters( int depthValue ) {
		final float k1 = 0.1236f;
		final float k2 = 2842.5f;
		final float k3 = 1.1863f;
		final float k4 = 0.0370f;
		
		if ( depthValue < 2047 ) {
			return 100 * (k1 * tan((depthValue / k2) + k3) - k4);
		}
		return 0.0f;
	}
	
	
	// not in use
	public float rawDepthToMeters( int depthValue ) {
		if ( depthValue < 2047 ) {
			return (float)(1.0f / ((double)(depthValue) * -0.0030711016 + 3.3309495161 ));
		}
		return 0.0f;
	}
	
	
	public Vec3D depthToWorld( int x, int y, int depthValue ) {
		final double fx_d = 1.0 / 5.9421434211923247e+02;
		final double fy_d = 1.0 / 5.9104053696870778e+02;
		final double cx_d = 3.3930780975300314e+02;
		final double cy_d = 2.4273913761751615e+02;
		
		Vec3D result = new Vec3D();
		double depth = depthLookUp[depthValue];
		result.x = (float)((x - cx_d) * depth * fx_d);
		result.y = (float)((y - cy_d) * depth * fy_d);
		result.z = (float)(depth);
		return result;
	}
	
	
	public void stop() {
		kinect.quit();
		super.stop();
	}
	
}
