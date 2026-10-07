package com.cooptest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class GrabMechanic {
   public static final HashMap<UUID, UUID> holding = new HashMap<>();
   public static final HashMap<UUID, UUID> heldBy = new HashMap<>();
   private static MinecraftServer cachedServer = null;
   public static final HashMap<UUID, Boolean> shieldMode = new HashMap<>();
   public static final HashMap<UUID, Long> shieldSwapCooldown = new HashMap<>();
   public static final HashMap<UUID, ArmorStand> shieldArmorStands = new HashMap<>();
   private static final long SHIELD_SWAP_COOLDOWN_MS = 1000L;
   private static final HashMap<UUID, GrabMechanic.PendingThrow> pendingThrows = new HashMap<>();
   private static final HashMap<UUID, GrabMechanic.ThrownPlayerData> thrownPlayers = new HashMap<>();
   public static final HashMap<UUID, Boolean> elytraBoostRequests = new HashMap<>();
   public static final HashMap<UUID, float[]> airMovementInput = new HashMap<>();
   private static final double AIR_CONTROL_STRENGTH = 0.025;

   public static boolean tryGrab(ServerPlayer holder, ServerPlayer held) {
      if (holder == held) {
         return false;
      }

      if (holder.distanceTo(held) > 3.0F) {
         return false;
      }

      if (holding.containsKey(holder.getUUID())) {
         return false;
      }

      if (heldBy.containsKey(held.getUUID())) {
         return false;
      }

      if (PushInteractionHandler.hasPushImmunity(held.getUUID())) {
         return false;
      }

      PoseState holderPose = PoseNetworking.poseStates.getOrDefault(holder.getUUID(), PoseState.NONE);
      if (holderPose != PoseState.GRAB_READY) {
         return false;
      }

      if (holder.isPassenger() && holder.getVehicle() == held) {
         return false;
      }

      if (held.isPassenger() && held.getVehicle() == holder) {
         return false;
      }

      boolean success = held.startRiding(holder, true, true);
      if (!success) {
         return false;
      }

      holding.put(holder.getUUID(), held.getUUID());
      heldBy.put(held.getUUID(), holder.getUUID());
      PoseNetworking.poseStates.put(holder.getUUID(), PoseState.GRAB_HOLDING);
      PoseNetworking.poseStates.put(held.getUUID(), PoseState.GRABBED);
      if (holder.level().getServer() != null) {
         PoseNetworking.broadcastPoseChange(holder.level().getServer(), holder.getUUID(), PoseState.GRAB_HOLDING);
         PoseNetworking.broadcastPoseChange(holder.level().getServer(), held.getUUID(), PoseState.GRABBED);
         GrabNetworking.broadcastGrabState(holder.level().getServer(), holder.getUUID(), held.getUUID(), true);
         ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(holder);

         for (ServerPlayer p : holder.level().getServer().getPlayerList().getPlayers()) {
            p.connection.send(packet);
         }
      }

      holder.level().playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
      return true;
   }

   public static boolean tryThrow(ServerPlayer holder, float power) {
      UUID heldId = holding.get(holder.getUUID());
      if (heldId == null) {
         return false;
      }

      if (isInShieldMode(holder.getUUID())) {
         holder.sendOverlayMessage(Component.literal("§cSwitch to throw mode first! (Press V)"));
         return false;
      }

      ServerPlayer held = holder.level().getServer().getPlayerList().getPlayer(heldId);
      if (held == null) {
         cleanupGrab(holder.getUUID());
         return false;
      }

      if (!holder.isCreative()) {
         if (holder.getFoodData().getFoodLevel() < 6) {
            holder.sendOverlayMessage(Component.literal("§cToo hungry to throw!"));
            return false;
         }

         holder.getFoodData().addExhaustion(2.0F);
      }

      Vec3 lookDir = holder.getViewVector(1.0F);
      float scaledPower = 0.5F + 2.0F * power;
      double horizX = lookDir.x * scaledPower * 1.6;
      double horizZ = lookDir.z * scaledPower * 1.6;
      double verticalBase = 0.4 + lookDir.y * 0.6;
      double maxVertical = 1.8;
      double verticalVel = Math.min(verticalBase + power * 0.6, maxVertical);
      if (lookDir.y < 0.0) {
         verticalVel = Math.max(0.3, verticalVel);
      }

      Vec3 throwVelocity = new Vec3(horizX, verticalVel, horizZ);
      Vec3 releasePos = holder.position().add(lookDir.scale(1.5).multiply(1.0, 0.0, 1.0)).add(0.0, 0.5, 0.0);
      held.stopRiding();
      holding.remove(holder.getUUID());
      heldBy.remove(held.getUUID());
      PoseNetworking.poseStates.put(holder.getUUID(), PoseState.NONE);
      if (holder.level().getServer() != null) {
         PoseNetworking.broadcastPoseChange(holder.level().getServer(), holder.getUUID(), PoseState.NONE);
         PoseNetworking.broadcastAnimState(holder, 0);
         GrabNetworking.broadcastGrabState(holder.level().getServer(), holder.getUUID(), held.getUUID(), false);
         ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(holder);

         for (ServerPlayer p : holder.level().getServer().getPlayerList().getPlayers()) {
            p.connection.send(packet);
         }
      }

      float throwYaw = holder.getYRot();
      float throwPitch = holder.getXRot();
      held.snapTo(releasePos.x, releasePos.y, releasePos.z, throwYaw, throwPitch);
      held.setYHeadRot(throwYaw);
      held.connection.teleport(releasePos.x, releasePos.y, releasePos.z, throwYaw, throwPitch);
      pendingThrows.put(held.getUUID(), new GrabMechanic.PendingThrow(holder, held, throwVelocity, 3));
      holder.level()
         .playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F + power * 0.4F);
      spawnThrowParticles(holder, held);
      return true;
   }

   private static void spawnThrowParticles(ServerPlayer holder, ServerPlayer held) {
      ServerLevel world = holder.level();
      Vec3 pos = holder.position();

      for (int i = 0; i < 10; i++) {
         double offsetX = (world.random.nextDouble() - 0.5) * 0.5;
         double offsetY = world.random.nextDouble() * 0.5 + 0.5;
         double offsetZ = (world.random.nextDouble() - 0.5) * 0.5;
         world.sendParticles(ParticleTypes.CLOUD, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, 0.0, 0.0, 0.0, 0.05);
      }
   }

   public static void tick(MinecraftServer server) {
      cachedServer = server;
      tickShieldMode(server);

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         UUID playerId = player.getUUID();
         PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
         if (pose == PoseState.GRABBED && !player.isPassenger() && !thrownPlayers.containsKey(playerId) && !pendingThrows.containsKey(playerId)) {
            heldBy.remove(playerId);
            PoseNetworking.poseStates.put(playerId, PoseState.NONE);
            PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
            PoseNetworking.broadcastAnimState(player, 0);
         }
      }

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (player.isPassenger() && player.getVehicle() instanceof ServerPlayer carrier) {
            PoseState pose = PoseNetworking.poseStates.get(player.getUUID());
            if (pose == PoseState.GRABBED) {
               float carrierYaw = carrier.getYRot();
               player.setYRot(carrierYaw);
               player.setYBodyRot(carrierYaw);
               player.setYHeadRot(carrierYaw);
            }
         }
      }

      Iterator<Entry<UUID, GrabMechanic.PendingThrow>> throwIterator = pendingThrows.entrySet().iterator();

      while (throwIterator.hasNext()) {
         Entry<UUID, GrabMechanic.PendingThrow> entry = throwIterator.next();
         GrabMechanic.PendingThrow pending = entry.getValue();
         pending.ticksRemaining--;
         if (pending.ticksRemaining <= 0) {
            ServerPlayer held = pending.held;
            if (held != null && held.isAlive()) {
               held.setDeltaMovement(pending.velocity);
               held.syncVelocity = true;
               held.connection.send(new ClientboundSetEntityMotionPacket(held));
               boolean wasOnFire = held.isOnFire();
               thrownPlayers.put(held.getUUID(), new GrabMechanic.ThrownPlayerData(held.getY(), wasOnFire, pending.velocity));
            }

            throwIterator.remove();
         }
      }

      Set<UUID> toRemove = new HashSet<>();

      for (Entry<UUID, GrabMechanic.ThrownPlayerData> entry : new HashMap<>(thrownPlayers).entrySet()) {
         UUID playerId = entry.getKey();
         GrabMechanic.ThrownPlayerData data = entry.getValue();
         ServerPlayer player = server.getPlayerList().getPlayer(playerId);
         if (player == null) {
            toRemove.add(playerId);
         } else {
            data.ticksFlying++;
            Vec3 vel = player.getDeltaMovement();
            if (vel.horizontalDistanceSqr() > 0.01) {
               float velocityYaw = (float)Math.toDegrees(Math.atan2(-vel.x, vel.z));
               player.setYRot(velocityYaw);
               player.setYBodyRot(velocityYaw);
               player.setYHeadRot(velocityYaw);
            }

            float[] moveInput = airMovementInput.get(playerId);
            if (moveInput != null && (Math.abs(moveInput[0]) > 0.01F || Math.abs(moveInput[1]) > 0.01F)) {
               float yawRad = (float)Math.toRadians(player.getYRot());
               float forward = moveInput[0];
               float strafe = moveInput[1];
               double driftX = (-strafe * Math.cos(yawRad) - forward * Math.sin(yawRad)) * 0.025;
               double driftZ = (-strafe * Math.sin(yawRad) + forward * Math.cos(yawRad)) * 0.025;
               Vec3 currentVel = player.getDeltaMovement();
               player.setDeltaMovement(currentVel.add(driftX, 0.0, driftZ));
               player.syncVelocity = true;
            }

            long timeSinceThrow = System.currentTimeMillis() - data.throwTimeMs;
            if (!data.elytraBoostUsed
               && timeSinceThrow < 2000L
               && elytraBoostRequests.remove(playerId) != null
               && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
               Vec3 look = player.getViewVector(1.0F);
               double boostStrength = 1.5;
               player.setDeltaMovement(player.getDeltaMovement().add(look.x * boostStrength, look.y * boostStrength + 0.5, look.z * boostStrength));
               player.syncVelocity = true;
               player.startFallFlying();
               player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
               player.level().sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY(), player.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
               data.elytraBoostUsed = true;
               PoseNetworking.poseStates.put(playerId, PoseState.NONE);
               PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
               airMovementInput.remove(playerId);
               toRemove.add(playerId);
            } else {
               if (data.lastPos != null) {
                  checkWallCollision(player, data);
               }

               data.lastPos = player.position();
               if (data.ticksFlying % 2 == 0) {
                  spawnTrailParticles(player);
                  if (data.wasOnFire && player.isOnFire()) {
                     spawnFireTrail(player, data);
                  }
               }

               checkForNearbyCreepers(player);
               if (data.ticksFlying >= 10) {
                  boolean onGround = player.onGround();
                  boolean inWater = player.isInWater();
                  boolean closeToGround = isCloseToGround(player);
                  boolean isFalling = player.getDeltaMovement().y < -0.1;
                  if (onGround || inWater || closeToGround && isFalling) {
                     PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                     PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
                     ServerPlayer landedPlayer = server.getPlayerList().getPlayer(playerId);
                     if (landedPlayer != null && !SpinHandler.isSpinning(playerId)) {
                        PoseNetworking.broadcastAnimState(landedPlayer, 0);
                     }

                     airMovementInput.remove(playerId);
                     if (data.wasOnFire) {
                        createFireExplosion(player);
                     }

                     spawnLandingParticles(player);
                     toRemove.add(playerId);
                  }
               }

               int maxTicks = SpinHandler.isSpinning(playerId) ? 1200 : 100;
               if (data.ticksFlying > maxTicks) {
                  PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                  PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
                  if (!SpinHandler.isSpinning(playerId)) {
                     ServerPlayer timedOutPlayer = server.getPlayerList().getPlayer(playerId);
                     if (timedOutPlayer != null) {
                        PoseNetworking.broadcastAnimState(timedOutPlayer, 0);
                     }
                  }

                  airMovementInput.remove(playerId);
                  toRemove.add(playerId);
               }
            }
         }
      }

      thrownPlayers.keySet().removeAll(toRemove);
   }

   private static void checkWallCollision(ServerPlayer player, GrabMechanic.ThrownPlayerData data) {
      ServerLevel world = player.level();
      Vec3 currentPos = player.position();
      Vec3 velocity = player.getDeltaMovement();
      double speed = velocity.horizontalDistance();
      if (!(speed < 0.3)) {
         Vec3 direction = velocity.normalize();

         for (double dist = 0.3; dist <= 1.5; dist += 0.3) {
            Vec3 checkPos = currentPos.add(direction.scale(dist));

            for (double yOff = 0.0; yOff <= 1.8; yOff += 0.9) {
               BlockPos blockPos = new BlockPos((int)Math.floor(checkPos.x), (int)Math.floor(checkPos.y + yOff), (int)Math.floor(checkPos.z));
               BlockState blockState = world.getBlockState(blockPos);
               if (!blockState.isAir() && canBreakBlock(blockState, world, blockPos)) {
                  float hardness = blockState.getDestroySpeed(world, blockPos);
                  float damage = calculateWallDamage(hardness, speed);
                  world.destroyBlock(blockPos, true, player);
                  world.playSound(null, blockPos, blockState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                  if (damage > 0.0F) {
                     player.hurtServer(world, world.damageSources().flyIntoWall(), damage);
                  }

                  player.setDeltaMovement(velocity.scale(0.7));
                  player.syncVelocity = true;
                  player.connection.send(new ClientboundSetEntityMotionPacket(player));
                  world.sendParticles(ParticleTypes.CRIT, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.1);
               }
            }
         }
      }
   }

   private static boolean canBreakBlock(BlockState state, ServerLevel world, BlockPos pos) {
      if (state.is(Blocks.BEDROCK)) {
         return false;
      } else if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) {
         return false;
      } else if (state.is(Blocks.REINFORCED_DEEPSLATE)) {
         return false;
      } else if (state.is(Blocks.END_PORTAL_FRAME)) {
         return false;
      } else if (state.is(Blocks.BARRIER)) {
         return false;
      } else if (state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.CHAIN_COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK)) {
         return false;
      } else if (state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) {
         return false;
      } else if (state.is(Blocks.CHEST)
         || state.is(Blocks.TRAPPED_CHEST)
         || state.is(Blocks.ENDER_CHEST)
         || state.is(Blocks.BARREL)
         || state.is(Blocks.SHULKER_BOX)
         || state.is(Blocks.FURNACE)
         || state.is(Blocks.BLAST_FURNACE)
         || state.is(Blocks.SMOKER)
         || state.is(Blocks.BREWING_STAND)
         || state.is(Blocks.ENCHANTING_TABLE)
         || state.is(Blocks.ANVIL)
         || state.is(Blocks.CHIPPED_ANVIL)
         || state.is(Blocks.DAMAGED_ANVIL)
         || state.is(Blocks.CRAFTING_TABLE)
         || state.is(Blocks.CARTOGRAPHY_TABLE)
         || state.is(Blocks.FLETCHING_TABLE)
         || state.is(Blocks.GRINDSTONE)
         || state.is(Blocks.LOOM)
         || state.is(Blocks.SMITHING_TABLE)
         || state.is(Blocks.STONECUTTER)
         || state.is(Blocks.LECTERN)
         || state.is(Blocks.BEACON)
         || state.is(Blocks.RESPAWN_ANCHOR)
         || state.is(Blocks.LODESTONE)) {
         return false;
      } else if (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)) {
         return false;
      } else if (state.is(BlockTags.SIGNS) || state.is(BlockTags.ALL_HANGING_SIGNS)) {
         return false;
      } else if (state.is(BlockTags.BEDS)) {
         return false;
      } else if (!state.is(BlockTags.BUTTONS) && !state.is(Blocks.LEVER)) {
         float hardness = state.getDestroySpeed(world, pos);
         return hardness < 0.0F ? false : !(hardness > 25.0F);
      } else {
         return false;
      }
   }

   private static float calculateWallDamage(float hardness, double speed) {
      float baseDamage = hardness * 0.8F;
      float speedMultiplier = (float)Math.min(2.0, speed);
      return Math.max(1.0F, Math.min(10.0F, baseDamage * speedMultiplier));
   }

   private static void spawnTrailParticles(ServerPlayer player) {
      ServerLevel world = player.level();
      Vec3 pos = player.position();
      world.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z, 1, 0.1, 0.1, 0.1, 0.02);
   }

   private static void spawnLandingParticles(ServerPlayer player) {
      ServerLevel world = player.level();
      Vec3 pos = player.position();
      BlockPos groundPos = player.blockPosition().below();
      BlockState groundBlock = world.getBlockState(groundPos);
      world.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 1, 0.0, 0.0, 0.0, 0.0);

      for (int i = 0; i < 24; i++) {
         double angle = (Math.PI * 2) * i / 24.0;
         double offsetX = Math.cos(angle) * 0.8;
         double offsetZ = Math.sin(angle) * 0.8;
         double velX = Math.cos(angle) * 0.3;
         double velZ = Math.sin(angle) * 0.3;
         world.sendParticles(ParticleTypes.CLOUD, pos.x + offsetX, pos.y + 0.2, pos.z + offsetZ, 1, velX, 0.1, velZ, 0.05);
         world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x + offsetX * 0.5, pos.y + 0.1, pos.z + offsetZ * 0.5, 1, velX * 0.5, 0.2, velZ * 0.5, 0.02);
      }

      if (!groundBlock.isAir()) {
         BlockParticleOption blockParticle = new BlockParticleOption(ParticleTypes.BLOCK, groundBlock);

         for (int i = 0; i < 30; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * 1.5;
            double offsetZ = (world.random.nextDouble() - 0.5) * 1.5;
            double velY = world.random.nextDouble() * 0.5 + 0.2;
            world.sendParticles(blockParticle, pos.x + offsetX, pos.y + 0.1, pos.z + offsetZ, 1, 0.0, velY, 0.0, 0.15);
         }
      }

      world.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.3, pos.z, 15, 0.5, 0.3, 0.5, 0.05);
      world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.5, pos.z, 10, 0.5, 0.5, 0.5, 0.3);
      world.playSound(null, pos.x, pos.y, pos.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.2F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 0.8F);
   }

   private static boolean isCloseToGround(ServerPlayer player) {
      if (player.onGround()) {
         return true;
      }

      if (player.getDeltaMovement().y >= 0.0) {
         return false;
      }

      double maxDistance = 1.0;
      double startY = player.getY();

      for (double checkY = startY; checkY > startY - maxDistance; checkY -= 0.5) {
         BlockPos blockPos = player.blockPosition().atY((int)checkY - 1);
         BlockState blockState = player.level().getBlockState(blockPos);
         if (!blockState.isAir() && blockState.isRedstoneConductor(player.level(), blockPos)) {
            return true;
         }
      }

      return false;
   }

   public static boolean tryDrop(ServerPlayer holder) {
      UUID heldId = holding.get(holder.getUUID());
      if (heldId == null) {
         return false;
      }

      ServerPlayer held = holder.level().getServer().getPlayerList().getPlayer(heldId);
      if (held == null) {
         cleanupGrab(holder.getUUID());
         return false;
      }

      if (isInShieldMode(holder.getUUID())) {
         holder.removeEffect(MobEffects.SLOWNESS);
      }

      held.stopRiding();
      cleanupGrab(holder.getUUID());
      PoseNetworking.poseStates.put(holder.getUUID(), PoseState.NONE);
      PoseNetworking.poseStates.put(held.getUUID(), PoseState.NONE);
      if (holder.level().getServer() != null) {
         PoseNetworking.broadcastPoseChange(holder.level().getServer(), holder.getUUID(), PoseState.NONE);
         PoseNetworking.broadcastPoseChange(holder.level().getServer(), held.getUUID(), PoseState.NONE);
         PoseNetworking.broadcastAnimState(holder, 0);
         PoseNetworking.broadcastAnimState(held, 0);
         GrabNetworking.broadcastGrabState(holder.level().getServer(), holder.getUUID(), held.getUUID(), false);
         ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(holder);

         for (ServerPlayer p : holder.level().getServer().getPlayerList().getPlayers()) {
            p.connection.send(packet);
         }
      }

      holder.level().playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 0.8F);
      return true;
   }

   public static boolean tryEscape(ServerPlayer held) {
      UUID holderId = heldBy.get(held.getUUID());
      if (holderId == null) {
         return false;
      }

      ServerPlayer holder = held.level().getServer().getPlayerList().getPlayer(holderId);
      if (holder != null && isInShieldMode(holderId)) {
         holder.removeEffect(MobEffects.SLOWNESS);
      }

      held.stopRiding();
      cleanupGrab(holderId);
      PoseNetworking.poseStates.put(held.getUUID(), PoseState.NONE);
      if (holder != null) {
         PoseNetworking.poseStates.put(holder.getUUID(), PoseState.NONE);
         PoseNetworking.broadcastPoseChange(held.level().getServer(), holder.getUUID(), PoseState.NONE);
         PoseNetworking.broadcastAnimState(holder, 0);
      }

      if (held.level().getServer() != null) {
         PoseNetworking.broadcastPoseChange(held.level().getServer(), held.getUUID(), PoseState.NONE);
         PoseNetworking.broadcastAnimState(held, 0);
         GrabNetworking.broadcastGrabState(held.level().getServer(), holderId, held.getUUID(), false);
         if (holder != null) {
            ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(holder);

            for (ServerPlayer p : held.level().getServer().getPlayerList().getPlayers()) {
               p.connection.send(packet);
            }
         }

         Vec3 escapePos = held.position().add(0.0, 0.1, 0.0);
         held.teleportTo(escapePos.x, escapePos.y, escapePos.z);
      }

      held.level().playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 1.2F);
      return true;
   }

   public static void cleanupGrab(UUID holderUuid) {
      UUID heldUuid = holding.remove(holderUuid);
      if (heldUuid != null) {
         heldBy.remove(heldUuid);
      }

      shieldMode.remove(holderUuid);
      shieldSwapCooldown.remove(holderUuid);
      ArmorStand armorStand = shieldArmorStands.remove(holderUuid);
      if (armorStand != null && !armorStand.isRemoved()) {
         armorStand.discard();
      }
   }

   public static boolean isHolding(ServerPlayer player) {
      return holding.containsKey(player.getUUID());
   }

   public static boolean isBeingHeld(ServerPlayer player) {
      return heldBy.containsKey(player.getUUID());
   }

   public static void forceRelease(UUID playerUuid) {
      UUID heldUuid = holding.remove(playerUuid);
      if (heldUuid != null) {
         heldBy.remove(heldUuid);
      }

      UUID holderUuid = heldBy.remove(playerUuid);
      if (holderUuid != null) {
         holding.remove(holderUuid);
      }

      PoseNetworking.poseStates.remove(playerUuid);
      pendingThrows.remove(playerUuid);
      thrownPlayers.remove(playerUuid);
   }

   private static void spawnFireTrail(ServerPlayer player, GrabMechanic.ThrownPlayerData data) {
      ServerLevel world = player.level();
      Vec3 pos = player.position();
      world.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.5, pos.z, 3, 0.2, 0.2, 0.2, 0.02);
      world.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.3, pos.z, 2, 0.1, 0.1, 0.1, 0.01);
      if (data.ticksFlying % 4 == 0) {
         BlockPos groundPos = player.blockPosition().below();

         for (int i = 0; i < 5; i++) {
            BlockState belowState = world.getBlockState(groundPos);
            if (!belowState.isAir()) {
               BlockPos firePos = groundPos.above();
               BlockState fireSpot = world.getBlockState(firePos);
               if (fireSpot.isAir()) {
                  world.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
               }
               break;
            }

            groundPos = groundPos.below();
         }
      }
   }

   private static void checkForNearbyCreepers(ServerPlayer player) {
      ServerLevel world = player.level();
      double checkRadius = 4.0;

      for (Creeper creeper : world.getEntitiesOfClass(
         Creeper.class, player.getBoundingBox().inflate(checkRadius), creeperx -> creeperx.isAlive() && player.distanceTo(creeperx) <= checkRadius
      )) {
         world.explode(creeper, creeper.getX(), creeper.getY(), creeper.getZ(), 3.0F, ExplosionInteraction.MOB);
         creeper.discard();
         world.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0F, 1.0F);
      }

      for (Ghast ghast : world.getEntitiesOfClass(
         Ghast.class, player.getBoundingBox().inflate(checkRadius), ghastx -> ghastx.isAlive() && player.distanceTo(ghastx) <= checkRadius
      )) {
         Vec3 ghastPos = ghast.position();
         ghast.hurtServer(world, world.damageSources().playerAttack(player), 1000.0F);
         world.playSound(null, ghastPos.x, ghastPos.y, ghastPos.z, ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 2.0F, 1.0F);
         world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ghastPos.x, ghastPos.y, ghastPos.z, 3, 0.5, 0.5, 0.5, 0.0);
         world.sendParticles(ParticleTypes.CLOUD, ghastPos.x, ghastPos.y, ghastPos.z, 30, 1.5, 1.5, 1.5, 0.1);
         world.sendParticles(ParticleTypes.FLAME, ghastPos.x, ghastPos.y, ghastPos.z, 20, 1.0, 1.0, 1.0, 0.2);
         player.sendOverlayMessage(Component.literal("§6§l\ud83d\udca5 GHAST OBLITERATED! \ud83d\udca5"));
      }
   }

   private static void createFireExplosion(ServerPlayer player) {
      ServerLevel world = player.level();
      Vec3 pos = player.position();
      world.explode(player, pos.x, pos.y, pos.z, 2.0F, true, ExplosionInteraction.MOB);
      world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 1.5F, 1.0F);
      player.hurtServer(world, world.damageSources().onFire(), 16.0F);

      for (int i = 0; i < 20; i++) {
         double offsetX = (world.random.nextDouble() - 0.5) * 3.0;
         double offsetY = world.random.nextDouble() * 2.0;
         double offsetZ = (world.random.nextDouble() - 0.5) * 3.0;
         world.sendParticles(ParticleTypes.FLAME, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, 0.0, 0.0, 0.0, 0.1);
      }
   }

   public static void setAirMovementInput(UUID playerId, float forward, float strafe) {
      if (thrownPlayers.containsKey(playerId)) {
         airMovementInput.put(playerId, new float[]{forward, strafe});
      }
   }

   public static boolean isPlayerThrown(UUID playerId) {
      return thrownPlayers.containsKey(playerId);
   }

   public static void requestElytraBoost(UUID playerId) {
      if (thrownPlayers.containsKey(playerId)) {
         elytraBoostRequests.put(playerId, true);
      }
   }

   public static void fullCleanup(UUID playerId) {
      thrownPlayers.remove(playerId);
      pendingThrows.remove(playerId);
      elytraBoostRequests.remove(playerId);
      airMovementInput.remove(playerId);
      shieldMode.remove(playerId);
      shieldSwapCooldown.remove(playerId);
      ArmorStand armorStand = shieldArmorStands.remove(playerId);
      if (armorStand != null && !armorStand.isRemoved()) {
         armorStand.discard();
      }

      if (holding.containsKey(playerId)) {
         UUID heldId = holding.get(playerId);
         heldBy.remove(heldId);
         holding.remove(playerId);
         if (cachedServer != null) {
            ServerPlayer heldPlayer = cachedServer.getPlayerList().getPlayer(heldId);
            if (heldPlayer != null) {
               heldPlayer.stopRiding();
               PoseNetworking.poseStates.put(heldId, PoseState.NONE);
               PoseNetworking.broadcastPoseChange(cachedServer, heldId, PoseState.NONE);
               PoseNetworking.broadcastAnimState(heldPlayer, 0);
               ClientboundSetPassengersPacket passPacket = new ClientboundSetPassengersPacket(heldPlayer);

               for (ServerPlayer p : cachedServer.getPlayerList().getPlayers()) {
                  try {
                     p.connection.send(passPacket);
                  } catch (Exception var10) {
                  }
               }
            }

            try {
               GrabNetworking.broadcastGrabState(cachedServer, playerId, heldId, false);
            } catch (Exception var9) {
            }
         }

         ArmorStand holderArmorStand = shieldArmorStands.remove(playerId);
         if (holderArmorStand != null && !holderArmorStand.isRemoved()) {
            holderArmorStand.discard();
         }
      }

      if (heldBy.containsKey(playerId)) {
         UUID holderId = heldBy.get(playerId);
         holding.remove(holderId);
         heldBy.remove(playerId);
         shieldMode.remove(holderId);
         if (cachedServer != null) {
            PoseNetworking.poseStates.put(playerId, PoseState.NONE);
            PoseNetworking.broadcastPoseChange(cachedServer, playerId, PoseState.NONE);

            try {
               GrabNetworking.broadcastGrabState(cachedServer, holderId, playerId, false);
            } catch (Exception var8) {
            }

            ServerPlayer holder = cachedServer.getPlayerList().getPlayer(holderId);
            if (holder != null) {
               PoseNetworking.poseStates.put(holderId, PoseState.NONE);
               PoseNetworking.broadcastPoseChange(cachedServer, holderId, PoseState.NONE);
               PoseNetworking.broadcastAnimState(holder, 0);
            }
         }

         ArmorStand holderArmorStand = shieldArmorStands.remove(holderId);
         if (holderArmorStand != null && !holderArmorStand.isRemoved()) {
            holderArmorStand.discard();
         }
      }
   }

   public static boolean toggleShieldMode(ServerPlayer holder) {
      UUID holderId = holder.getUUID();
      if (!holding.containsKey(holderId)) {
         return false;
      }

      Long cooldownEnd = shieldSwapCooldown.get(holderId);
      if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
         long remaining = (cooldownEnd - System.currentTimeMillis()) / 100L;
         holder.sendOverlayMessage(Component.literal("§cSwap cooldown! " + remaining / 10.0 + "s"));
         return false;
      }

      UUID heldId = holding.get(holderId);
      ServerPlayer held = holder.level().getServer().getPlayerList().getPlayer(heldId);
      if (held == null) {
         return false;
      }

      boolean currentMode = shieldMode.getOrDefault(holderId, false);
      boolean newMode = !currentMode;
      shieldMode.put(holderId, newMode);
      shieldSwapCooldown.put(holderId, System.currentTimeMillis() + 1000L);
      if (newMode) {
         holder.sendOverlayMessage(Component.literal("§b\ud83d\udee1 HUMAN SHIELD MODE"));
         held.sendOverlayMessage(Component.literal("§c⚠ You are now a SHIELD!"));
         held.stopRiding();
         ServerLevel world = holder.level();
         double yaw = Math.toRadians(holder.getYRot());
         double forwardX = -Math.sin(yaw) * 0.8;
         double forwardZ = Math.cos(yaw) * 0.8;
         ArmorStand armorStand = new ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, world);
         armorStand.setPos(holder.getX() + forwardX, holder.getY() - 0.5, holder.getZ() + forwardZ);
         armorStand.setYRot(holder.getYRot());
         armorStand.setInvisible(true);
         armorStand.setNoGravity(true);
         armorStand.setPermanentlyInvulnerable(true);
         armorStand.setSilent(true);
         world.addFreshEntity(armorStand);
         shieldArmorStands.put(holderId, armorStand);
         held.startRiding(armorStand, true, true);
         world.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.2F);
         holder.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 999999, 1, false, false, true));
         PoseNetworking.poseStates.put(heldId, PoseState.NONE);
         PoseNetworking.broadcastPoseChange(holder.level().getServer(), heldId, PoseState.NONE);
         PoseNetworking.poseStates.put(holderId, PoseState.GRAB_HOLDING);
         PoseNetworking.broadcastAnimState(held, 29);
         PoseNetworking.broadcastAnimState(holder, 28);
         ClientboundSetPassengersPacket holderPacket = new ClientboundSetPassengersPacket(holder);

         for (ServerPlayer p : holder.level().getServer().getPlayerList().getPlayers()) {
            p.connection.send(holderPacket);
         }

         ArmorStand as = shieldArmorStands.get(holderId);
         if (as != null) {
            ClientboundSetPassengersPacket asPacket = new ClientboundSetPassengersPacket(as);

            for (ServerPlayer p : holder.level().getServer().getPlayerList().getPlayers()) {
               p.connection.send(asPacket);
            }
         }

         broadcastShieldMode(holder.level().getServer(), holderId, heldId, true);
      } else {
         holder.sendOverlayMessage(Component.literal("§e✋ THROW MODE"));
         held.sendOverlayMessage(Component.literal("§eBack to throw mode"));
         held.stopRiding();
         ArmorStand armorStand = shieldArmorStands.remove(holderId);
         if (armorStand != null) {
            armorStand.discard();
         }

         if (!holder.isPassenger() || holder.getVehicle() != held) {
            held.startRiding(holder, true, true);
         }

         PoseNetworking.poseStates.put(heldId, PoseState.GRABBED);
         PoseNetworking.broadcastPoseChange(held.level().getServer(), heldId, PoseState.GRABBED);
         PoseNetworking.broadcastAnimState(holder, 3);
         holder.removeEffect(MobEffects.SLOWNESS);
         ClientboundSetPassengersPacket holderPacket = new ClientboundSetPassengersPacket(holder);

         for (ServerPlayer p : holder.level().getServer().getPlayerList().getPlayers()) {
            p.connection.send(holderPacket);
         }

         holder.level().playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
         broadcastShieldMode(holder.level().getServer(), holderId, heldId, false);
      }

      return true;
   }

   public static void tickShieldMode(MinecraftServer server) {
      Iterator<Entry<UUID, ArmorStand>> iterator = shieldArmorStands.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, ArmorStand> entry = iterator.next();
         UUID holderId = entry.getKey();
         ArmorStand armorStand = entry.getValue();
         if (!shieldMode.getOrDefault(holderId, false)) {
            armorStand.discard();
            iterator.remove();
         } else {
            ServerPlayer holder = server.getPlayerList().getPlayer(holderId);
            if (holder != null && !armorStand.isRemoved()) {
               UUID heldId = holding.get(holderId);
               if (heldId == null) {
                  armorStand.discard();
                  iterator.remove();
                  shieldMode.remove(holderId);
                  shieldSwapCooldown.remove(holderId);
                  holder.removeEffect(MobEffects.SLOWNESS);
                  PoseNetworking.poseStates.put(holderId, PoseState.NONE);
                  PoseNetworking.broadcastPoseChange(holder.level().getServer(), holderId, PoseState.NONE);
                  PoseNetworking.broadcastAnimState(holder, 0);
                  GrabNetworking.broadcastGrabState(server, holderId, heldId, false);
                  ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(holder);

                  for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                     p.connection.send(packet);
                  }

                  holder.sendOverlayMessage(Component.literal("§c Shield dropped!"));
               } else {
                  ServerPlayer heldPlayer = server.getPlayerList().getPlayer(heldId);
                  if (heldPlayer != null && heldPlayer.isAlive()) {
                     double yaw = Math.toRadians(holder.getYRot());
                     double forwardX = -Math.sin(yaw) * 0.8;
                     double forwardZ = Math.cos(yaw) * 0.8;
                     double newX = holder.getX() + forwardX;
                     double newY = holder.getY() - 0.5;
                     double newZ = holder.getZ() + forwardZ;
                     armorStand.setPos(newX, newY, newZ);
                     armorStand.setYRot(holder.getYRot());
                     if (armorStand.getFirstPassenger() instanceof ServerPlayer shieldPlayer) {
                        float heldYaw = holder.getYRot();
                        shieldPlayer.setYRot(heldYaw);
                        shieldPlayer.setYBodyRot(heldYaw);
                        shieldPlayer.setYHeadRot(heldYaw);
                        PoseNetworking.AnimStateSyncPayload animPayload = new PoseNetworking.AnimStateSyncPayload(shieldPlayer.getUUID(), 29);
                        ServerPlayNetworking.send(holder, animPayload);
                        ClientboundTeleportEntityPacket posPacket = ClientboundTeleportEntityPacket.teleport(
                           shieldPlayer.getId(), PositionMoveRotation.of(shieldPlayer), Set.of(), shieldPlayer.onGround()
                        );
                        holder.connection.send(posPacket);
                        ClientboundTeleportEntityPacket standPacket = ClientboundTeleportEntityPacket.teleport(
                           armorStand.getId(), PositionMoveRotation.of(armorStand), Set.of(), armorStand.onGround()
                        );
                        holder.connection.send(standPacket);
                     }
                  } else {
                     armorStand.discard();
                     iterator.remove();
                     shieldMode.remove(holderId);
                     shieldSwapCooldown.remove(holderId);
                     holding.remove(holderId);
                     heldBy.remove(heldId);
                     holder.removeEffect(MobEffects.SLOWNESS);
                     PoseNetworking.poseStates.put(holderId, PoseState.NONE);
                     PoseNetworking.broadcastPoseChange(holder.level().getServer(), holderId, PoseState.NONE);
                     PoseNetworking.broadcastAnimState(holder, 0);
                     GrabNetworking.broadcastGrabState(server, holderId, heldId, false);
                     ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(holder);

                     for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.connection.send(packet);
                     }

                     holder.sendOverlayMessage(Component.literal("§c Shield died!"));
                  }
               }
            } else {
               if (!armorStand.isRemoved()) {
                  armorStand.discard();
               }

               iterator.remove();
               shieldMode.remove(holderId);
            }
         }
      }
   }

   public static boolean isInShieldMode(UUID holderId) {
      return shieldMode.getOrDefault(holderId, false);
   }

   public static ServerPlayer getShieldPlayer(ServerPlayer holder) {
      if (!isInShieldMode(holder.getUUID())) {
         return null;
      }

      UUID heldId = holding.get(holder.getUUID());
      return heldId == null ? null : holder.level().getServer().getPlayerList().getPlayer(heldId);
   }

   private static void broadcastShieldMode(MinecraftServer server, UUID holderId, UUID heldId, boolean enabled) {
      GrabMechanic.ShieldModePayload payload = new GrabMechanic.ShieldModePayload(holderId, heldId, enabled);

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void registerShieldDamageEvent() {
      ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> {
         if (entity instanceof ServerPlayer holder) {
            if (isInShieldMode(holder.getUUID())) {
               ServerPlayer shield = getShieldPlayer(holder);
               if (shield != null && shield.isAlive()) {
                  shield.hurtServer(shield.level(), source, amount);
                  return false;
               }
            }

            return true;
         } else {
            return true;
         }
      });
   }

   private static class PendingThrow {
      ServerPlayer holder;
      ServerPlayer held;
      Vec3 velocity;
      int ticksRemaining;

      PendingThrow(ServerPlayer holder, ServerPlayer held, Vec3 velocity, int delay) {
         this.holder = holder;
         this.held = held;
         this.velocity = velocity;
         this.ticksRemaining = delay;
      }
   }

   public record ShieldModePayload(UUID holderId, UUID heldId, boolean enabled) implements CustomPacketPayload {
      public static final Type<GrabMechanic.ShieldModePayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "shield_mode"));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }

      public static void register() {
         PayloadTypeRegistry.clientboundPlay().register(ID, StreamCodec.ofMember((payload, buf) -> {
            buf.writeUUID(payload.holderId);
            buf.writeUUID(payload.heldId);
            buf.writeBoolean(payload.enabled);
         }, buf -> new GrabMechanic.ShieldModePayload(buf.readUUID(), buf.readUUID(), buf.readBoolean())));
      }
   }

   private static class ThrownPlayerData {
      double startY;
      int ticksFlying;
      boolean wasOnFire;
      Vec3 lastPos;
      Vec3 velocity;
      long throwTimeMs;
      boolean elytraBoostUsed;

      ThrownPlayerData(double startY, boolean wasOnFire, Vec3 velocity) {
         this.startY = startY;
         this.ticksFlying = 0;
         this.wasOnFire = wasOnFire;
         this.lastPos = null;
         this.velocity = velocity;
         this.throwTimeMs = System.currentTimeMillis();
         this.elytraBoostUsed = false;
      }
   }
}
