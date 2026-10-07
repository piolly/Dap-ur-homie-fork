package com.cooptest.meme;

import java.util.UUID;

public class SpinYeetSession {
   public final UUID grabberUuid;
   public final UUID grabbedUuid;
   public float spinAngle = 0.0F;
   public float currentSpeed = 0.05F;
   public int tickCount = 0;
   public boolean active = true;
   public static final float BASE_SPEED = 0.05F;
   public static final float MAX_SPEED = 0.8F;
   public static final float ORBIT_RADIUS = 1.5F;
   public static final float HEIGHT_OFFSET = 0.6F;
   public static final int SPIN_RAMP_TICKS = 100;
   public static final float MAX_YEET_HORIZONTAL = 7.0F;
   public static final float MIN_YEET_HORIZONTAL = 0.8F;
   public static final float MAX_YEET_VERTICAL = 2.5F;
   public static final float MIN_YEET_VERTICAL = 0.3F;
   public static final double GRAB_RANGE_SQ = 4.0;
   public static final double BROADCAST_RANGE_SQ = 2500.0;
   public static final float MAX_TILT_DEGREES = 90.0F;
   public static final int TILT_RAMP_TICKS = 100;
   public static final float GRABBER_SPIN_MULTIPLIER = 1.0F;
   public static final int YEET_DURATION_TICKS = 100;
   public static final float YEET_CLEAR_RADIUS = 2.0F;

   public SpinYeetSession(UUID grabberUuid, UUID grabbedUuid) {
      this.grabberUuid = grabberUuid;
      this.grabbedUuid = grabbedUuid;
   }
}
