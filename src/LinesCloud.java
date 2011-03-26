import processing.core.*;
import processing.opengl.*;

import codeanticode.glgraphics.*;

import org.openkinect.*;
import org.openkinect.processing.*;

import toxi.math.*;
import toxi.geom.*;

import peasy.*;
import controlP5.*;



public class LinesCloud extends PApplet {
	
	Kinect kinect;
	
	//PeasyCam cam;
	ControlP5 controlP5;
	Slider distanceThresholdSlider;
	Slider kinectTiltSlider;
	Slider cameraDistanceSlider;
	Slider2D cameraRotationSlider;
	PMatrix3D currCameraMatrix;
	PGraphics3D g3;
	
	GLModel points;
	
	static final int GRID_WIDTH			= 640;
	static final int GRID_HEIGHT 		= 480;
	static final int GRID_ROWS 			= 80;
	static final int GRID_COLUMNS 		= 35;
	
	static final float EASING			= 0.075f;
	
	float gridCenterX, gridCenterY;
	float gridSpaceX, gridSpaceY;
	float distanceToCenter;
	float distanceThreshold;
	
	int totalPoints;
	
	Vec3D[] pointsPos = new Vec3D[GRID_WIDTH * GRID_HEIGHT];
	
	float[] depthLookUp = new float[2048];
	
	boolean saveFrames = false;
	
	float rot = 0;
	
	
	
	public void setup() {
		size( 1280, 768, GLConstants.GLGRAPHICS );
		
		// Setup kinect
		kinect = new Kinect(this);
		kinect.start();
		kinect.enableDepth(true);
		kinect.processDepthImage(false);

		// Setup scene camera
		//cam = new PeasyCam( this, 250 );
		//cam.lookAt( 0, 0, 0, 30 );
		
		
		g3 = (PGraphics3D)g;
		
		
		// Controls
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
		
		
		// Get depth lookup table
		for ( int i = 0; i < depthLookUp.length; i++ ) depthLookUp[i] = rawDepthToCentimeters( i );
		
		
		// Initialize points mesh
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
		      	pointsPos[offset] = pv;
		    }
		}
		
		totalPoints = GRID_ROWS * GRID_COLUMNS;
		
		points = new GLModel(this, totalPoints, LINES, GLModel.DYNAMIC);

		  points.initColors();
		  points.beginUpdateColors();
		  for ( int i = 0; i < totalPoints; i++ ) points.updateColor( i, 255, 255, 225 );
		  points.endUpdateColors(); 

		  points.beginUpdateVertices();
		  for ( int i = 0; i < totalPoints; i++ ) points.updateVertex( i, pointsPos[i].x, pointsPos[i].y, pointsPos[i].z);
		  points.endUpdateVertices();   
		  points.setLineWidth( 2.0f );
		  
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
		  
		  points.initIndices( totalIndices );
		  points.updateIndices( indices );
		
	}
	
	
	
	public void draw() {
		/*if ( controlP5.window(this).isMouseOver() )
			cam.setActive(false);
		else 
			cam.setActive(true);*/
		
		int[] depth = kinect.getRawDepth();
		
		GLGraphics renderer = (GLGraphics)g;
	    renderer.beginGL(); 
	    
		background( 0 );
		
		
		hint( ENABLE_DEPTH_TEST );
		
		beginCamera();
		camera( 0.0f, 0.0f, cameraDistanceSlider.value(),
				0.0f, 0.0f, 0.0f, 
			    0.0f, 1.0f, 0.0f );
		//rotateX( radians(45 - cameraRotationSlider.arrayValue()[1]) );
		//rotateY( radians(45 - cameraRotationSlider.arrayValue()[0]) );
		rot += 0.0075f;
		rotateY( rot );
		rotateX( sin( frameCount * 0.01f ) * 0.25f );
		
		points.beginUpdateVertices();
		for ( int i = 0; i < totalPoints; i++ ) {
			float distance = getDistanceAt( (int)pointsPos[i].x, (int)pointsPos[i].y, depth ) * 2.5f;
			if ( distance > distanceThreshold || distance == 0 ) distance = distanceThreshold;
			pointsPos[i].z += (distance - pointsPos[i].z) * EASING;
			points.updateVertex( i, pointsPos[i].x, pointsPos[i].y, pointsPos[i].z );
		}
		points.endUpdateVertices();
		
		
		points.beginUpdateColors();
		for ( int i = 0; i < totalPoints; i++ ) {		
			float dt = pointsPos[i].z / distanceThreshold;
			
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
			
			points.updateColor( i, ctr, ctg, ctb );
		}
		points.endUpdateColors();
		
		
		pushMatrix();
		translate( -gridCenterX, -gridCenterY, -distanceToCenter );
		renderer.model( points );
		popMatrix();
		
		endCamera();
		
		renderer.endGL();
		
		//stats();
		gui();
		
		
		// Save frames
		if ( saveFrames == true ) saveFrame( "line-####.jpg" );
			
	}
	
	
	
	public void stats() {
		fill( 255 );
		textMode( SCREEN );
		String info  = "Kinect FPS: " + (int)kinect.getDepthFPS() + "\n";
			   info += "Processing FPS: " + (int)frameRate + "\n";
			   info += "Number of Points: " + totalPoints + "\n";
		text( info, 10, 16 );
	}
	
	public void gui() {
		hint( DISABLE_DEPTH_TEST );
		
		currCameraMatrix = new PMatrix3D( g3.camera );
		camera();
		controlP5.draw();
		g3.camera = currCameraMatrix;
		
		//System.out.println( slider2d.arrayValue()[0] );
		distanceThreshold = distanceThresholdSlider.value();
		
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
