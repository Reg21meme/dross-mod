package com.reg21meme.dross.villager;

import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** What the hub's survivors do (see {@link Survivor}): listen, wait, follow and wander. */
final class SurvivorGoals
{
    private SurvivorGoals() {}

    /** After a right-click, stand still and look at the player for a moment ({@link Survivor#LISTEN_TICKS}). */
    static final class ListenGoal extends Goal
    {
        private final Survivor survivor;

        ListenGoal(Survivor survivor)
        {
            this.survivor = survivor;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            return this.survivor.listener() != null;
        }

        @Override
        public void start()
        {
            this.survivor.getNavigation().stop();
        }

        @Override
        public void tick()
        {
            Player player = this.survivor.listener();
            if (player != null)
            {
                this.survivor.getLookControl().setLookAt(player, 30.0F, 30.0F);
            }
        }
    }

    /**
     * The mentor waits (stands still, turned towards her student) while the student is more than
     * {@link Survivor#WAIT_DISTANCE} blocks away, until it's within {@link Survivor#WAIT_RESUME}.
     */
    static final class WaitForStudentGoal extends Goal
    {
        private final Survivor survivor;

        WaitForStudentGoal(Survivor survivor)
        {
            this.survivor = survivor;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            return this.survivor.getRole() == SurvivorRole.MENTOR && this.studentFurtherThan(Survivor.WAIT_DISTANCE);
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.survivor.getRole() == SurvivorRole.MENTOR && this.studentFurtherThan(Survivor.WAIT_RESUME);
        }

        private boolean studentFurtherThan(double distance)
        {
            Survivor student = this.survivor.partner();
            return student != null && this.survivor.distanceToSqr(student) > distance * distance;
        }

        @Override
        public void start()
        {
            this.survivor.getNavigation().stop();
        }

        @Override
        public void tick()
        {
            Survivor student = this.survivor.partner();
            if (student != null)
            {
                this.survivor.getLookControl().setLookAt(student, 10.0F, this.survivor.getMaxHeadXRot());
            }
        }
    }

    /**
     * The student follows the mentor: it walks once she's more than {@link Survivor#FOLLOW_START} blocks away and stops
     * {@link Survivor#FOLLOW_STOP} from her, faster when it has fallen far behind.
     */
    static final class FollowMentorGoal extends Goal
    {
        /** The path to her is worked out again this often (ticks), as she moves. */
        private static final int REPATH_TICKS = 10;
        /** If there's no path to her at all, it waits this long (ticks) before trying again. */
        private static final int NO_PATH_RETRY_TICKS = 20;

        private final Survivor survivor;
        private int repathIn;
        private int retryAt;

        FollowMentorGoal(Survivor survivor)
        {
            this.survivor = survivor;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private Survivor mentor()
        {
            return this.survivor.getRole() == SurvivorRole.STUDENT ? this.survivor.partner() : null;
        }

        @Override
        public boolean canUse()
        {
            if (this.survivor.tickCount < this.retryAt)
            {
                return false;
            }
            Survivor mentor = this.mentor();
            return mentor != null && this.survivor.distanceToSqr(mentor) > Survivor.FOLLOW_START * Survivor.FOLLOW_START;
        }

        @Override
        public boolean canContinueToUse()
        {
            Survivor mentor = this.mentor();
            return mentor != null && !this.survivor.getNavigation().isDone()
                    && this.survivor.distanceToSqr(mentor) > Survivor.FOLLOW_STOP * Survivor.FOLLOW_STOP;
        }

        @Override
        public void start()
        {
            this.repathIn = 0;
        }

        @Override
        public void stop()
        {
            this.survivor.getNavigation().stop();
        }

        @Override
        public void tick()
        {
            Survivor mentor = this.mentor();
            if (mentor == null)
            {
                return;
            }
            this.survivor.getLookControl().setLookAt(mentor, 10.0F, this.survivor.getMaxHeadXRot());
            if (--this.repathIn <= 0)
            {
                this.repathIn = this.adjustedTickDelay(REPATH_TICKS);
                double speed = this.survivor.distanceToSqr(mentor) > Survivor.CATCH_UP_DISTANCE * Survivor.CATCH_UP_DISTANCE
                        ? Survivor.STUDENT_CATCH_UP_SPEED : Survivor.STUDENT_FOLLOW_SPEED;
                if (!this.survivor.getNavigation().moveTo(mentor, speed))
                {
                    this.retryAt = this.survivor.tickCount + NO_PATH_RETRY_TICKS; // no way to her from here: try again in a moment
                }
            }
        }
    }

    /**
     * Wandering, for the mentor (and a student whose mentor isn't there): every so often it walks, slowly, either to one
     * of the places in its grounds ({@link SurvivorGrounds}: its own rest spot, the hall, the gate, the steps, the road
     * and the ground in front of the walls, picked by weight) or on a short stroll nearby. It pauses at each place:
     * longest at its rest spot. A far place may take a few walks; one it can't get to is given up. It never picks a place
     * outside its area or by the exit portal.
     */
    static final class WanderGoal extends Goal
    {
        /** The first walk starts this long after it's loaded (ticks)... */
        private static final int FIRST_WALK_MIN = 60;
        private static final int FIRST_WALK_MAX = 300;
        /** ...it rests at its own spot this long (30 to 70 seconds)... */
        private static final int REST_MIN = 600;
        private static final int REST_MAX = 1400;
        /** ...pauses at another place this long (10 to 25 seconds)... */
        private static final int PAUSE_MIN = 200;
        private static final int PAUSE_MAX = 500;
        /** ...after a short stroll this long (5 to 12 seconds)... */
        private static final int STROLL_PAUSE_MIN = 100;
        private static final int STROLL_PAUSE_MAX = 240;
        /** ...and between the walks to a far place this long. */
        private static final int LEG_PAUSE_MIN = 10;
        private static final int LEG_PAUSE_MAX = 40;
        /** If no stroll could be found, it tries again after this long. */
        private static final int RETRY_TICKS = 40;
        /** One walk ends after this long (ticks) even if it hasn't arrived. */
        private static final int MAX_WALK_TICKS = 30 * 20;
        /** Walks towards one place before it's given up. */
        private static final int MAX_LEGS = 4;
        /** How often a walk goes to one of the places (otherwise it's a stroll nearby). */
        private static final float PLACE_CHANCE = 0.65F;
        /** A stroll goes up to this far (blocks) and this far up or down. */
        private static final int STROLL_RADIUS = 8;
        private static final int STROLL_HEIGHT = 4;
        /** It has arrived at a place when it's this close (blocks). */
        private static final double ARRIVED = 2.5D;

        private final Survivor survivor;
        private int nextWalkTick = -1;
        /** The place it's heading for, or null for a short stroll. */
        @Nullable
        private BlockPos heading;
        private boolean headingHome;
        private int legs;
        @Nullable
        private Vec3 target;
        private int walkStartTick;

        WanderGoal(Survivor survivor)
        {
            this.survivor = survivor;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            if (!this.survivor.wandersOnItsOwn() || this.survivor.getRestSpot() == null)
            {
                return false;
            }
            if (this.nextWalkTick < 0)
            {
                this.nextWalkTick = this.survivor.tickCount + this.between(FIRST_WALK_MIN, FIRST_WALK_MAX);
            }
            if (this.survivor.tickCount < this.nextWalkTick)
            {
                return false;
            }
            this.target = this.chooseTarget();
            if (this.target == null)
            {
                this.nextWalkTick = this.survivor.tickCount + RETRY_TICKS;
                return false;
            }
            return true;
        }

        @Override
        public void start()
        {
            this.walkStartTick = this.survivor.tickCount;
            Vec3 to = this.target;
            if (to != null)
            {
                this.survivor.getNavigation().moveTo(to.x, to.y, to.z, this.survivor.walkSpeed());
            }
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.survivor.wandersOnItsOwn() && !this.survivor.getNavigation().isDone()
                    && this.survivor.tickCount - this.walkStartTick < MAX_WALK_TICKS;
        }

        @Override
        public void stop()
        {
            this.survivor.getNavigation().stop();
            BlockPos place = this.heading;
            if (place == null)
            {
                this.pause(STROLL_PAUSE_MIN, STROLL_PAUSE_MAX);
            }
            else if (this.isAt(place))
            {
                this.heading = null;
                if (this.headingHome)
                {
                    this.pause(REST_MIN, REST_MAX);
                }
                else
                {
                    this.pause(PAUSE_MIN, PAUSE_MAX);
                }
            }
            else if (++this.legs >= MAX_LEGS)
            {
                this.heading = null; // it can't get there: give up on that place for now
                this.pause(STROLL_PAUSE_MIN, STROLL_PAUSE_MAX);
            }
            else
            {
                this.pause(LEG_PAUSE_MIN, LEG_PAUSE_MAX); // not there yet: keep going after a breath
            }
        }

        @Nullable
        private Vec3 chooseTarget()
        {
            if (this.heading != null)
            {
                return Vec3.atBottomCenterOf(this.heading);
            }
            if (this.survivor.getRandom().nextFloat() < PLACE_CHANCE)
            {
                BlockPos place = this.pickPlace();
                if (place != null)
                {
                    this.heading = place;
                    this.headingHome = place.equals(this.survivor.getRestSpot());
                    this.legs = 0;
                    return Vec3.atBottomCenterOf(place);
                }
            }
            return this.strollNearby();
        }

        /** One of its places, by weight (its rest spot among them), not the one it's at now; null if there's none. */
        @Nullable
        private BlockPos pickPlace()
        {
            BlockPos home = this.survivor.getRestSpot();
            List<SurvivorGrounds.Waypoint> places = this.survivor.getWaypoints();
            int total = 0;
            if (home != null && !this.isAt(home))
            {
                total += SurvivorGrounds.HOME_WEIGHT;
            }
            for (SurvivorGrounds.Waypoint place : places)
            {
                if (!this.isAt(place.pos()))
                {
                    total += place.weight();
                }
            }
            if (total <= 0)
            {
                return null;
            }
            int roll = this.survivor.getRandom().nextInt(total);
            if (home != null && !this.isAt(home))
            {
                roll -= SurvivorGrounds.HOME_WEIGHT;
                if (roll < 0)
                {
                    return home;
                }
            }
            for (SurvivorGrounds.Waypoint place : places)
            {
                if (!this.isAt(place.pos()))
                {
                    roll -= place.weight();
                    if (roll < 0)
                    {
                        return place.pos();
                    }
                }
            }
            return null;
        }

        /** A random spot nearby it may stand on (vanilla's random walk, kept inside its area), or null. */
        @Nullable
        private Vec3 strollNearby()
        {
            for (int i = 0; i < 5; i++)
            {
                Vec3 spot = LandRandomPos.getPos(this.survivor, STROLL_RADIUS, STROLL_HEIGHT);
                if (spot != null && this.survivor.isGoodPlaceToStand(BlockPos.containing(spot)))
                {
                    return spot;
                }
            }
            return null;
        }

        private boolean isAt(BlockPos place)
        {
            return this.survivor.distanceToSqr(Vec3.atBottomCenterOf(place)) < ARRIVED * ARRIVED;
        }

        private void pause(int min, int max)
        {
            this.nextWalkTick = this.survivor.tickCount + this.between(min, max);
        }

        private int between(int min, int max)
        {
            return min + this.survivor.getRandom().nextInt(max - min + 1);
        }
    }
}
