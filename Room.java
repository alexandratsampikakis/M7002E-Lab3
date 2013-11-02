import com.jme3.app.SimpleApplication;
import com.jme3.cinematic.MotionPath;
import com.jme3.cinematic.MotionPathListener;
import com.jme3.cinematic.events.MotionTrack;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture.WrapMode;
import com.jme3.util.SkyFactory;

public class Room extends SimpleApplication {

	private TerrainQuad terrain;
	Material mat_terrain;
	float x, y, z, ry, s;
	Node shootables;
	Geometry mark;
	float xCamCoord, yCamCoord, zCamCoord, moveRandom;
	ParticleEmitter fire, debris;
	Spatial wall;
	MotionPath path;
	MotionTrack motionControl;
	
	public static void main(String[] args) {
		Room app = new Room();
		app.start();
	}

	@Override
	public void simpleInitApp() {
		initCrossHairs(); // a "+" in the middle of the screen to help aiming
		initKeys(); // load custom key mappings
		initMark(); // a red sphere to mark the hit
		flyCam.setMoveSpeed(200);

		/** 1. Create terrain material and load four textures into it. */
		mat_terrain = new Material(assetManager, "Common/MatDefs/Terrain/Terrain.j3md");

		/** 1.1) Add ALPHA map (for red-blue-green coded splat textures) */
		mat_terrain.setTexture("Alpha", assetManager.loadTexture("Textures/Terrain/splat/alphamap.png"));

		/** 1.2) Add GRASS texture into the red layer (Tex1). */
		Texture grass = assetManager.loadTexture("Textures/Terrain/splat/grass.jpg");
		grass.setWrap(WrapMode.Repeat);
		mat_terrain.setTexture("Tex1", grass);
		mat_terrain.setFloat("Tex1Scale", 64f);

		/** 1.3) Add DIRT texture into the green layer (Tex2) */
		Texture dirt = assetManager.loadTexture("Textures/Terrain/splat/dirt.jpg");
		dirt.setWrap(WrapMode.Repeat);
		mat_terrain.setTexture("Tex2", dirt);
		mat_terrain.setFloat("Tex2Scale", 32f);

		/** 1.4) Add ROAD texture into the blue layer (Tex3) */
		Texture rock = assetManager.loadTexture("Textures/Terrain/splat/road.jpg");
		rock.setWrap(WrapMode.Repeat);
		mat_terrain.setTexture("Tex3", rock);
		mat_terrain.setFloat("Tex3Scale", 128f);

		// Adding sky
		rootNode.attachChild(SkyFactory.createSky(assetManager, "Textures/Sky/Bright/BrightSky.dds", false));

		/** 2. Create the height map */
		AbstractHeightMap heightmap = null;
		Texture heightMapImage = assetManager.loadTexture("Textures/Terrain/splat/mountains512.png");
		heightmap = new ImageBasedHeightMap(heightMapImage.getImage());
		heightmap.load();

		/**
		 * 3. We have prepared material and heightmap. Now we create the actual
		 * terrain: 3.1) Create a TerrainQuad and name it "my terrain". 3.2) A
		 * good value for terrain tiles is 64x64 -- so we supply 64+1=65. 3.3)
		 * We prepared a heightmap of size 512x512 -- so we supply 512+1=513.
		 * 3.4) As LOD step scale we supply Vector3f(1,1,1). 3.5) We supply the
		 * prepared heightmap itself.
		 */
		int patchSize = 65;
		terrain = new TerrainQuad("my terrain", patchSize, 513, heightmap.getHeightMap());

		/**
		 * 4. We give the terrain its material, position & scale it, and attach it.
		 */
		terrain.setMaterial(mat_terrain);
		terrain.setLocalTranslation(x, y, z);
		terrain.setLocalScale(2f, 1f, 2f);
		rootNode.attachChild(terrain);

		/** 5. The LOD (level of detail) depends on were the camera is: */
		TerrainLodControl control = new TerrainLodControl(terrain, getCamera());
		terrain.addControl(control);

		shootables = new Node("Shootables");
		terrain.attachChild(shootables);

		// Draw the boxes and stores them in the vector.
		int random = 0;
		while (random < 4) {
			x = (float) (-700.0 * Math.random()) + 500;
			z = (float) (-700.0 * Math.random()) + 500;
			ry = (float) (-700.0 * Math.random()) + 500;
			moveRandom = (float) (-700.0 * Math.random()) + 500;
			s = (float) Math.random();
			shootables.attachChild(drawBox());
			random++;
		}
		
		path = new MotionPath();
        path.addWayPoint(new Vector3f(xCamCoord, yCamCoord, 0));
        path.addWayPoint(new Vector3f(xCamCoord-100, yCamCoord, 0));
        path.addWayPoint(new Vector3f(xCamCoord-100, yCamCoord, 0-50));
        path.addWayPoint(new Vector3f(xCamCoord-50, yCamCoord, 0-25));
        path.enableDebugShape(assetManager, rootNode);
        path.disableDebugShape();
        path.setCycle(true);
        motionControl = new MotionTrack(wall, path);
        motionControl.setDirectionType(MotionTrack.Direction.PathAndRotation);
        motionControl.setRotation(new Quaternion().fromAngleNormalAxis(-FastMath.HALF_PI, Vector3f.UNIT_Y));
        motionControl.setInitialDuration(10f);
        motionControl.setSpeed(1f);
        guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        final BitmapText wayPointsText = new BitmapText(guiFont, false);
        wayPointsText.setSize(guiFont.getCharSet().getRenderedSize());
        guiNode.attachChild(wayPointsText);

        path.addListener(new MotionPathListener() {
        	
            public void onWayPointReach(MotionTrack control, int wayPointIndex) {
                if (path.getNbWayPoints() == wayPointIndex + 1) {
                    wayPointsText.setText(control.getSpatial().getName() + "Finished!!! ");
                } else {
                    wayPointsText.setText(control.getSpatial().getName() + " Reached way point " + wayPointIndex);
                }
                wayPointsText.setLocalTranslation((cam.getWidth() - wayPointsText.getLineWidth()) / 2, cam.getHeight(), 0);
            }
        });
	}

	/**
	 * Draw a box.
	 */
	public Spatial drawBox() {
		Box box = new Box(Vector3f.ZERO, 3.5f, 3.5f, 1.0f);
		wall = new Geometry("Box", box);
		Material mat_brick = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mat_brick.setTexture("ColorMap", assetManager.loadTexture("Textures/Terrain/BrickWall/BrickWall.jpg"));
		wall.setMaterial(mat_brick);
		Vector2f xz = new Vector2f(x, z);
		y = terrain.getHeight(xz);
		wall.setLocalTranslation(x, y, z);
		wall.scale(s);
		wall.rotate( x, ry, z);
		terrain.attachChild(wall);
		return wall;
	}

	/**
	 * Setting fire.
	 */
	public void setFire() {
		fire = new ParticleEmitter("Emitter", ParticleMesh.Type.Triangle, 30);
		Material mat_red = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
		mat_red.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/flame.png"));
		fire.setMaterial(mat_red);
		fire.setImagesX(2);
		fire.setImagesY(2); // 2x2 texture animation
		fire.setEndColor(new ColorRGBA(1f, 0f, 0f, 1f)); // red
		fire.setStartColor(new ColorRGBA(1f, 1f, 0f, 0.5f)); // yellow
		fire.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 2, 0));
		fire.setStartSize(1.5f);
		fire.setEndSize(0.1f);
		fire.setGravity(0, 0, 0);
		fire.setLowLife(1f);
		fire.setHighLife(3f);
		fire.getParticleInfluencer().setVelocityVariation(0.3f);
		rootNode.attachChild(fire);

		debris = new ParticleEmitter("Debris", ParticleMesh.Type.Triangle, 10);
		Material debris_mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
		debris_mat.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/Debris.png"));
		debris.setMaterial(debris_mat);
		debris.setImagesX(3);
		debris.setImagesY(3); // 3x3 texture animation
		debris.setRotateSpeed(4);
		debris.setSelectRandomImage(true);
		debris.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 4, 0));
		debris.setStartColor(ColorRGBA.White);
		debris.setGravity(0, 6, 0);
		debris.getParticleInfluencer().setVelocityVariation(.60f);
		rootNode.attachChild(debris);
		debris.emitAllParticles();
	}

	/** Defining the "Shoot" action: Determine what was hit and how to respond. */
	private ActionListener actionListener = new ActionListener() {

		public void onAction(String name, boolean keyPressed, float tpf) {
			if (name.equals("Shoot") && !keyPressed) {
				CollisionResults results = new CollisionResults(); // 1. Reset results list.
				Ray ray = new Ray(cam.getLocation(), cam.getDirection()); // 2. Aim the ray from cam loc to cam direction.
				shootables.collideWith(ray, results); // 3. Collect intersections between Ray and Shootables in results list.
				
				System.out.println("----- Collisions? " + results.size() + "-----"); // 4. Print the results
				for (int i = 0; i < results.size(); i++) {
					float dist = results.getCollision(i).getDistance(); // For each hit, we know distance, impact point, name of geometry.
					Vector3f pt = results.getCollision(i).getContactPoint();
					String hit = results.getCollision(i).getGeometry().getName();
					
					// Setting the box that was hit with fire and start moving the box.
					setFire();
					fire.move(pt);
					debris.move(pt);
					motionControl.play();
					
					System.out.println("* Collision #" + i);
					System.out.println("  You shot " + hit + " at " + pt + ", " + dist + " wu away.");
				}
				if (results.size() > 0) { // 5. Use the results (we mark the hit object)
					CollisionResult closest = results.getClosestCollision(); // The closest collision point is what was truly hit.
					mark.setLocalTranslation(closest.getContactPoint()); // Let's interact - we mark the hit with a red dot.
					terrain.attachChild(mark);
				} else {
					terrain.detachChild(mark); // No hits? Then remove the red mark.
				}
			}
		}
	};

	/** A red ball that marks the last spot that was "hit" by the "shot". */
	protected void initMark() {
		Sphere sphere = new Sphere(30, 30, 0.2f);
		mark = new Geometry("BOOM!", sphere);
		Material mark_mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mark_mat.setColor("Color", ColorRGBA.Red);
		mark.setMaterial(mark_mat);
	}

	/** A centred plus sign to help the player aim. */
	protected void initCrossHairs() {
		guiNode.detachAllChildren();
		guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
		BitmapText ch = new BitmapText(guiFont, false);
		ch.setSize(guiFont.getCharSet().getRenderedSize() * 2);
		ch.setText("+"); // crosshairs
		xCamCoord = settings.getWidth() / 2 - guiFont.getCharSet().getRenderedSize() / 3 * 2;
		yCamCoord = settings.getHeight() / 2 + ch.getLineHeight() / 2;
		zCamCoord = 0;
		ch.setLocalTranslation(xCamCoord, yCamCoord, zCamCoord);
		guiNode.attachChild(ch);
	}

	/** Declaring the "Shoot" action and mapping to its triggers. */
	private void initKeys() {
		inputManager.addMapping("Shoot", new KeyTrigger(KeyInput.KEY_SPACE), new MouseButtonTrigger(MouseInput.BUTTON_LEFT)); 
		inputManager.addListener(actionListener, "Shoot");
	}
}
