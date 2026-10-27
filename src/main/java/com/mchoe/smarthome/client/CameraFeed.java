package com.mchoe.smarthome.client;

import com.mchoe.smarthome.SmartHomeMod;
import com.mchoe.smarthome.block.DeviceBlock;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 카메라 기기(도어카메라·로비폰)의 실시간 영상.
 * 클라이언트에 불러온 월드를 카메라 위치에서 광선 추적해 작은 텍스처로 만든다.
 * 카메라 앞 플레이어는 얼굴(스킨)을 덧그려 누가 왔는지 알아볼 수 있다.
 */
public final class CameraFeed {
    public static final int W = 128, H = 96;
    private static final double RANGE = 40.0;
    private static final double TAN_V = Math.tan(Math.toRadians(32));
    private static final double TAN_H = TAN_V * W / H;

    private static final Map<BlockPos, CameraFeed> FEEDS = new HashMap<>();
    private static int counter;

    public record Mark(float u, float v, float size, UUID id) {}

    public final BlockPos cam;
    public final ResourceLocation loc;
    private final DynamicTexture tex;
    private long lastUpdate = -1000;
    private long lastUsed;
    public boolean signal;
    public final List<Mark> marks = new ArrayList<>();

    private CameraFeed(BlockPos cam) {
        this.cam = cam;
        this.tex = new DynamicTexture(W, H, true);
        this.loc = new ResourceLocation(SmartHomeMod.MODID, "camera_feed/" + (counter++));
        Minecraft.getInstance().getTextureManager().register(loc, tex);
    }

    /** 영상을 가져온다 (interval 틱마다 새로 그림) */
    public static CameraFeed get(BlockPos cam, int interval) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        CameraFeed f = FEEDS.computeIfAbsent(cam.immutable(), CameraFeed::new);
        long t = level.getGameTime();
        f.lastUsed = t;
        if (t - f.lastUpdate >= interval || t < f.lastUpdate) {
            f.lastUpdate = t;
            f.render(level);
        }
        return f;
    }

    /** 한동안 안 쓴 영상 텍스처 해제 */
    public static void cleanup() {
        ClientLevel level = Minecraft.getInstance().level;
        Iterator<CameraFeed> it = FEEDS.values().iterator();
        while (it.hasNext()) {
            CameraFeed f = it.next();
            if (level == null || level.getGameTime() - f.lastUsed > 200 || level.getGameTime() < f.lastUsed) {
                Minecraft.getInstance().getTextureManager().release(f.loc);
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ 렌더링

    private void render(ClientLevel level) {
        NativeImage img = tex.getPixels();
        if (img == null) return;
        marks.clear();
        BlockState camState = level.isLoaded(cam) ? level.getBlockState(cam) : null;
        if (camState == null || !(camState.getBlock() instanceof DeviceBlock db) || !db.type.camera) {
            signal = false;
            noise(img, level.getGameTime());
            tex.upload();
            return;
        }
        signal = true;
        Direction facing = camState.getValue(DeviceBlock.FACING);
        double pitch = Math.toRadians(-10);
        Vec3 fwd = new Vec3(facing.getStepX() * Math.cos(pitch), Math.sin(pitch), facing.getStepZ() * Math.cos(pitch)).normalize();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(fwd).normalize();
        Vec3 origin = new Vec3(cam.getX() + 0.5 + facing.getStepX() * 0.45, cam.getY() + db.type.cameraY / 16.0,
                cam.getZ() + 0.5 + facing.getStepZ() * 0.45);

        List<Entity> ents = level.getEntities((Entity) null, new AABB(origin, origin).inflate(RANGE),
                e -> e instanceof LivingEntity && !e.isSpectator() && !e.isInvisible());
        Entity[] hitEnt = new Entity[W * H];

        double dayF = dayFactor(level);
        Vec3 sky = level.getSkyColor(origin, 1.0f);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        for (int j = 0; j < H; j++) {
            double dy = (1 - (j + 0.5) / H * 2) * TAN_V;
            for (int i = 0; i < W; i++) {
                double dx = ((i + 0.5) / W * 2 - 1) * TAN_H;
                Vec3 dir = fwd.add(right.scale(dx)).add(up.scale(dy)).normalize();
                int rgb = trace(level, origin, dir, mp, ents, hitEnt, j * W + i, dayF, sky, dy);
                img.setPixelRGBA(i, j, 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF));
            }
        }

        // 플레이어 얼굴 표시
        for (Entity e : ents) {
            if (!(e instanceof Player p)) continue;
            Vec3 v = p.getEyePosition().subtract(origin);
            double z = v.dot(fwd);
            if (z < 0.3) continue;
            double u = v.dot(right) / (z * TAN_H);
            double vv = v.dot(up) / (z * TAN_V);
            if (Math.abs(u) > 1 || Math.abs(vv) > 1) continue;
            float px = (float) ((u + 1) / 2), py = (float) ((1 - vv) / 2);
            int ix = Math.min(W - 1, (int) (px * W)), iy = Math.min(H - 1, (int) (py * H));
            if (hitEnt[iy * W + ix] != p) continue;
            float size = (float) (0.27 / (z * TAN_V));
            marks.add(new Mark(px, py, size, p.getUUID()));
        }
        tex.upload();
    }

    private static double dayFactor(ClientLevel level) {
        double t = (level.getDayTime() % 24000L) / 24000.0;
        double f = 0.55 + Math.cos((t - 0.25) * Math.PI * 2) * 0.75;
        return Math.max(0.15, Math.min(1.0, f));
    }

    private int trace(ClientLevel level, Vec3 o, Vec3 d, BlockPos.MutableBlockPos mp, List<Entity> ents,
                      Entity[] hitEnt, int idx, double dayF, Vec3 sky, double screenDy) {
        Vec3 end = o.add(d.scale(RANGE));
        int x = (int) Math.floor(o.x), y = (int) Math.floor(o.y), z = (int) Math.floor(o.z);
        int sx = d.x > 0 ? 1 : -1, sy = d.y > 0 ? 1 : -1, sz = d.z > 0 ? 1 : -1;
        double tdx = d.x == 0 ? Double.MAX_VALUE : Math.abs(1 / d.x);
        double tdy = d.y == 0 ? Double.MAX_VALUE : Math.abs(1 / d.y);
        double tdz = d.z == 0 ? Double.MAX_VALUE : Math.abs(1 / d.z);
        double tmx = d.x == 0 ? Double.MAX_VALUE : ((sx > 0 ? x + 1 - o.x : o.x - x) * tdx);
        double tmy = d.y == 0 ? Double.MAX_VALUE : ((sy > 0 ? y + 1 - o.y : o.y - y) * tdy);
        double tmz = d.z == 0 ? Double.MAX_VALUE : ((sz > 0 ? z + 1 - o.z : o.z - z) * tdz);
        double t = 0;
        double hitT = RANGE;
        int color = -1;
        Direction face = Direction.UP;
        Vec3 hitLoc = null;
        double waterT = -1;
        boolean glass = false;

        while (t < RANGE) {
            if (!(x == cam.getX() && y == cam.getY() && z == cam.getZ())) {
                mp.set(x, y, z);
                if (!level.isLoaded(mp)) break;
                BlockState st = level.getBlockState(mp);
                if (!st.isAir()) {
                    if (waterT < 0 && !st.getFluidState().isEmpty()) waterT = t;
                    VoxelShape shape = st.getShape(level, mp);
                    if (!shape.isEmpty()) {
                        BlockHitResult hr = shape.clip(o, end, mp);
                        if (hr != null) {
                            int col = st.getMapColor(level, mp).col;
                            if (col == 0) {
                                glass = true;
                            } else {
                                hitLoc = hr.getLocation();
                                hitT = hitLoc.distanceTo(o);
                                face = hr.getDirection();
                                color = col;
                                mp.set(x, y, z);
                                break;
                            }
                        }
                    }
                }
            }
            if (tmx < tmy && tmx < tmz) {
                x += sx;
                t = tmx;
                tmx += tdx;
            } else if (tmy < tmz) {
                y += sy;
                t = tmy;
                tmy += tdy;
            } else {
                z += sz;
                t = tmz;
                tmz += tdz;
            }
        }

        // 사람·동물
        Entity hitE = null;
        for (Entity e : ents) {
            Optional<Vec3> h = e.getBoundingBox().clip(o, end);
            if (h.isPresent()) {
                double dist = h.get().distanceTo(o);
                if (dist < hitT) {
                    hitT = dist;
                    hitE = e;
                    hitLoc = h.get();
                }
            }
        }

        int rgb;
        if (hitE != null) {
            hitEnt[idx] = hitE;
            AABB bb = hitE.getBoundingBox();
            if (hitE instanceof Player) {
                if (hitLoc.y > bb.maxY - 0.5) rgb = 0xC69C7A;
                else if (hitLoc.y > bb.maxY - 1.15) rgb = 0x2E8A9A;
                else rgb = 0x3A3A8A;
            } else {
                rgb = 0x8A7A62;
            }
            BlockPos lp = BlockPos.containing(hitLoc);
            rgb = shade(rgb, light(level, lp, dayF) * 0.9);
        } else if (color != -1) {
            double f = switch (face) {
                case UP -> 1.0;
                case DOWN -> 0.55;
                case NORTH, SOUTH -> 0.84;
                default -> 0.7;
            };
            BlockPos lp = mp.relative(face);
            double l = light(level, lp, dayF);
            int hx = (int) Math.floor(hitLoc.x * 8), hy = (int) Math.floor(hitLoc.y * 8), hz = (int) Math.floor(hitLoc.z * 8);
            int h = (hx * 73856093) ^ (hy * 19349663) ^ (hz * 83492791);
            double noise = 1.0 + (((h >>> 3) % 17) - 8) / 100.0;
            rgb = shade(color, f * l * noise);
        } else {
            double g = Math.max(0, Math.min(1, 0.5 + screenDy));
            int r = (int) (sky.x * 255 * (0.8 + 0.2 * g)), gg = (int) (sky.y * 255 * (0.8 + 0.2 * g)), b = (int) (sky.z * 255);
            return (Math.min(255, r) << 16) | (Math.min(255, gg) << 8) | Math.min(255, b);
        }
        if (waterT >= 0 && waterT < hitT) rgb = mix(rgb, 0x3F76E4, 0.45);
        if (glass) rgb = mix(rgb, 0xC8E0F0, 0.12);
        if (hitT > 18) {
            int skyRgb = ((int) (sky.x * 255) << 16) | ((int) (sky.y * 255) << 8) | (int) (sky.z * 255);
            rgb = mix(rgb, skyRgb, Math.min(1, (hitT - 18) / (RANGE - 18)) * 0.8);
        }
        return rgb;
    }

    private static double light(ClientLevel level, BlockPos p, double dayF) {
        int block = level.getBrightness(LightLayer.BLOCK, p);
        int sky = level.getBrightness(LightLayer.SKY, p);
        double l = Math.max(block / 15.0, sky / 15.0 * dayF);
        return 0.2 + 0.8 * l;
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * f);
        int b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bb;
    }

    private static void noise(NativeImage img, long seed) {
        java.util.Random r = new java.util.Random(seed);
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++) {
                int g = 20 + r.nextInt(60);
                img.setPixelRGBA(i, j, 0xFF000000 | (g << 16) | (g << 8) | g);
            }
        }
    }
}
