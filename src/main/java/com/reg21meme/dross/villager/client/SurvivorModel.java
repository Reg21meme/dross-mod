package com.reg21meme.dross.villager.client;

import com.reg21meme.dross.quest.client.OracleSpellEffects;
import com.reg21meme.dross.villager.Survivor;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * CLIENT ONLY. The survivors' model: the vanilla villager's, whose folded arms the Blind Oracle raises over his head while
 * he casts his spell (the quest area's {@link OracleSpellEffects#armsRaised}), his head tipped back to the sky.
 */
public class SurvivorModel extends VillagerModel<Survivor>
{
    /** The folded arms' place and turn as vanilla's villager has them (its layer definition)... */
    private static final float ARMS_REST_TURN = -0.75F;
    private static final float ARMS_REST_Y = 3.0F;
    private static final float ARMS_REST_Z = -1.0F;
    /** ...and raised: swung up in front of his face, lifted to the top of his head. */
    private static final float ARMS_UP_TURN = -2.85F;
    private static final float ARMS_UP_Y = -4.5F;
    private static final float ARMS_UP_Z = -2.0F;
    /** His head tipped back this far (radians) with his arms up. */
    private static final float HEAD_BACK = -0.45F;
    /** The arms tremble a little while they're up (radians), this fast. */
    private static final float TREMBLE = 0.04F;
    private static final float TREMBLE_SPEED = 1.7F;

    private final ModelPart arms;
    private final ModelPart headPart;

    public SurvivorModel(ModelPart root)
    {
        super(root);
        this.arms = root.getChild("arms");
        this.headPart = root.getChild("head");
    }

    @Override
    public void setupAnim(Survivor survivor, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        super.setupAnim(survivor, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float raise = OracleSpellEffects.armsRaised(survivor, Mth.frac(ageInTicks));
        this.arms.xRot = Mth.lerp(raise, ARMS_REST_TURN, ARMS_UP_TURN) + raise * TREMBLE * Mth.sin(ageInTicks * TREMBLE_SPEED);
        this.arms.y = Mth.lerp(raise, ARMS_REST_Y, ARMS_UP_Y);
        this.arms.z = Mth.lerp(raise, ARMS_REST_Z, ARMS_UP_Z);
        if (raise > 0.0F)
        {
            this.headPart.xRot = Mth.lerp(raise, this.headPart.xRot, HEAD_BACK);
        }
    }
}
