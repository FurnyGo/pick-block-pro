package io.github.sjouwer.pickblockpro.picker;

import io.github.sjouwer.pickblockpro.config.ModConfig;
import io.github.sjouwer.pickblockpro.util.*;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SkullItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.state.property.Property;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

public class BlockPicker {
    private static BlockPicker INSTANCE;
    private final ModConfig config;
    private static final MinecraftClient minecraft = MinecraftClient.getInstance();

    public BlockPicker() {
        config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
    }

    public static BlockPicker getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new BlockPicker();
        }

        return INSTANCE;
    }

    public void pickBlock() {
        if (!config.blockPickEntities() && !config.blockPickBlocks()) {
            Chat.sendError(new TranslatableText("text.pick_block_pro.message.nothingToPick"));
            return;
        }

        HitResult hit = Raycast.getHit(config.blockPickRange(), config.blockFluidHandling(), !config.blockPickEntities());

        ItemStack item = null;
        if (hit.getType() == HitResult.Type.ENTITY) {
            item = getEntityItemStack(hit);
        }
        if (hit.getType() == HitResult.Type.BLOCK && config.blockPickBlocks()) {
            item = getBlockItemStack(hit);
        }
        if (hit.getType() == HitResult.Type.MISS) {
            item = getLightFromSun();
        }

        if (item != null) {
            Inventory.placeItemInsideInventory(item, config);
        }
    }

    private ItemStack getEntityItemStack(HitResult hit) {
        Entity entity = ((EntityHitResult) hit).getEntity();
        return entity.getPickBlockStack();
    }

    private ItemStack getBlockItemStack(HitResult hit) {
        BlockPos blockPos = ((BlockHitResult) hit).getBlockPos();
        BlockView world = minecraft.world;
        BlockState state = world.getBlockState(blockPos);
        ItemStack item = state.getBlock().getPickStack(world, blockPos, state);

        if (item.isEmpty()) {
            ItemStack extraItem = extraPickStackCheck(state);
            if (extraItem == null) {
                return null;
            }
            item = extraItem;
        }

        if (minecraft.player.getAbilities().creativeMode) {
            if (Screen.hasControlDown() && state.hasBlockEntity()) {
                BlockEntity blockEntity = world.getBlockEntity(blockPos);
                if (blockEntity != null) {
                    addBlockEntityNbt(item, blockEntity);
                }
            }
            if (Screen.hasAltDown()) {
                addBlockStateNbt(item, state);
            }
        }

        return item;
    }

    private ItemStack extraPickStackCheck(BlockState state) {
        if (state.isOf(Blocks.WATER)) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        if (state.isOf(Blocks.LAVA)) {
            return new ItemStack(Items.LAVA_BUCKET);
        }
        if ((state.isOf(Blocks.FIRE) || (state.isOf(Blocks.SOUL_FIRE)) && config.blockPickFire())) {
            return new ItemStack(Items.FLINT_AND_STEEL);
        }
        if (state.isOf(Blocks.SPAWNER)) {
            return new ItemStack(Items.SPAWNER);
        }

        return null;
    }

    private ItemStack getLightFromSun() {
        double skyAngle = minecraft.world.getSkyAngle(minecraft.getTickDelta()) + .25;
        if (skyAngle > 1) {
            skyAngle --;
        }
        skyAngle *= 360;

        Vec3d playerVector = minecraft.cameraEntity.getRotationVec(minecraft.getTickDelta());
        double playerAngle = Math.atan2(playerVector.y,playerVector.x) * 180 / Math.PI;
        if (playerAngle < 0) {
            playerAngle += 360;
        }

        double angleDifference = skyAngle - playerAngle;
        if (Math.abs(playerVector.z) < 0.076 && Math.abs(angleDifference) < 4.3) {
            //Do another raycast with a longer reach to make sure there is nothing in the way of the sun
            int viewDistance = minecraft.options.viewDistance * 32;
            HitResult hit = Raycast.getHit(viewDistance, RaycastContext.FluidHandling.ANY, true);
            if (hit.getType() == HitResult.Type.MISS) {
                ItemStack mainHandStack = minecraft.player.getMainHandStack();
                if (mainHandStack.isOf(Items.LIGHT)) {
                    cycleLightLevel(mainHandStack);
                }
                else {
                    return new ItemStack(Items.LIGHT);
                }
            }
        }

        return null;
    }

    private void cycleLightLevel(ItemStack light) {
        NbtCompound blockStateTag = light.getSubNbt("BlockStateTag");
        int newLightLvl;

        if (blockStateTag == null) {
            blockStateTag = new NbtCompound();
            newLightLvl = 0;
        }
        else {
            newLightLvl = blockStateTag.getInt("level") + 1;
        }
        if (newLightLvl == 16) {
            newLightLvl = 0;
        }

        blockStateTag.putInt("level", newLightLvl);
        light.setSubNbt("BlockStateTag", blockStateTag);

        PlayerInventory inventory = minecraft.player.getInventory();
        inventory.setStack(inventory.selectedSlot, light);
        Inventory.updateCreativeSlot(inventory.selectedSlot);
    }


    private void addBlockEntityNbt(ItemStack stack, BlockEntity blockEntity) {
        NbtCompound nbtCompound = blockEntity.createNbtWithIdentifyingData();
        NbtCompound nbtCompound3;
        if (stack.getItem() instanceof SkullItem && nbtCompound.contains("SkullOwner")) {
            nbtCompound3 = nbtCompound.getCompound("SkullOwner");
            stack.getOrCreateNbt().put("SkullOwner", nbtCompound3);
        } else {
            stack.setSubNbt("BlockEntityTag", nbtCompound);
            addNbtTag(stack);
        }
    }

    private void addBlockStateNbt(ItemStack stack, BlockState state) {
        NbtCompound nbtCompound = new NbtCompound();
        if (!state.getProperties().isEmpty()) {
            for (Property<?> property : state.getProperties()) {
                nbtCompound.putString(property.getName(), state.get(property).toString());
            }
            stack.setSubNbt("BlockStateTag", nbtCompound);
            addNbtTag(stack);
        }
    }

    private void addNbtTag(ItemStack stack) {
        NbtCompound nbtCompound = new NbtCompound();
        NbtList nbtList = new NbtList();
        nbtList.add(NbtString.of("\"(+NBT)\""));
        nbtCompound.put("Lore", nbtList);
        stack.setSubNbt("display", nbtCompound);
    }
}
