package rat.poison.scripts.aim

import rat.poison.game.*
import rat.poison.game.entity.*
import rat.poison.game.entity.EntityType.Companion.ccsPlayer
import rat.poison.settings.*
import rat.poison.utils.*
import org.jire.arrowhead.keyPressed
import rat.poison.curSettings
import rat.poison.strToBool
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

val target = AtomicLong(-1)
val bone = AtomicInteger(AIM_BONE)
val perfect = AtomicBoolean() //Perfect Aim boolean check, only for path aim
var boneTrig = false

internal fun reset() {
	target.set(-1L)
	perfect.set(false)
}

internal fun findTarget(position: Angle, angle: Angle, allowPerfect: Boolean,
						lockFOV: Int = curSettings["AIM_FOV"].toString().toInt(), BONE: Int = curSettings["AIM_BONE"].toString().toInt()): Player {
	var closestFOV = Double.MAX_VALUE
	var closestDelta = Double.MAX_VALUE
	var closestPlayer = -1L

	forEntities(ccsPlayer) result@{
		val entity = it.entity
		if (entity <= 0 || entity == me || !entity.canShoot()) {
			return@result false
		}

		if (BONE == -2) //Need to cleanup, needs to be switched to calcTarget
		{
			if (curSettings["BONE_TRIGGER_HB"]!!.strToBool() && curSettings["BONE_TRIGGER_BB"]!!.strToBool())
			{
				for (i in 3..8) //Check all body bones & head bone
				{
					//calcTarget(entity, position, angle, lockFOV, HEAD_BONE)
					val ePos: Angle = entity.bones(i)
					val distance = position.distanceTo(ePos)

					val dest = calculateAngle(me, ePos)

					val pitchDiff = Math.abs(angle.x - dest.x)
					val yawDiff = Math.abs(angle.y - dest.y)
					val fov = Math.abs(Math.sin(Math.toRadians(yawDiff)) * distance)
					val delta = Math.abs((Math.sin(Math.toRadians(pitchDiff)) + Math.sin(Math.toRadians(yawDiff))) * distance)

					if (delta <= lockFOV && delta <= closestDelta) {
						closestFOV = fov
						closestDelta = delta
						closestPlayer = entity
					}
				}
			}
			else if (curSettings["BONE_TRIGGER_BB"]!!.strToBool())
			{
				for (i in 3..7) //Check all body bones
				{
					//calcTarget(entity, position, angle, lockFOV, HEAD_BONE)
					val ePos: Angle = entity.bones(i)
					val distance = position.distanceTo(ePos)

					val dest = calculateAngle(me, ePos)

					val pitchDiff = Math.abs(angle.x - dest.x)
					val yawDiff = Math.abs(angle.y - dest.y)
					val fov = Math.abs(Math.sin(Math.toRadians(yawDiff)) * distance)
					val delta = Math.abs((Math.sin(Math.toRadians(pitchDiff)) + Math.sin(Math.toRadians(yawDiff))) * distance)

					if (delta <= lockFOV && delta <= closestDelta) {
						closestFOV = fov
						closestDelta = delta
						closestPlayer = entity
					}
				}
			}
			else
			{
				for (i in 3..8) //Check all body bones & head bone
				{
					//calcTarget(entity, position, angle, lockFOV, HEAD_BONE)
					val ePos: Angle = entity.bones(HEAD_BONE)
					val distance = position.distanceTo(ePos)

					val dest = calculateAngle(me, ePos)

					val pitchDiff = Math.abs(angle.x - dest.x)
					val yawDiff = Math.abs(angle.y - dest.y)
					val fov = Math.abs(Math.sin(Math.toRadians(yawDiff)) * distance)
					val delta = Math.abs((Math.sin(Math.toRadians(pitchDiff)) + Math.sin(Math.toRadians(yawDiff))) * distance)

					if (delta <= lockFOV && delta <= closestDelta) {
						closestFOV = fov
						closestDelta = delta
						closestPlayer = entity
					}
				}
			}
		}
		else { //Heavily cleaned up
			val arr = calcTarget(closestDelta, entity, position, angle, lockFOV, BONE)

			if (arr[0] != -1.0) {
				closestFOV = arr[0] as Double
				closestDelta = arr[1] as Double
				closestPlayer = arr[2] as Long
			}
		}
		return@result false
	}

	if (closestDelta == Double.MAX_VALUE || closestDelta < 0 || closestPlayer < 0) return -1
	if (curSettings["PERFECT_AIM"]!!.strToBool() && allowPerfect && closestFOV <= curSettings["PERFECT_AIM_FOV"].toString().toInt() && randInt(100 + 1) <= curSettings["PERFECT_AIM_CHANCE"].toString().toInt()) {
		perfect.set(true)
	}

	return closestPlayer
}

internal fun calcTarget(calcClosestDelta: Double, entity: Entity, position: Angle, angle: Angle, lockFOV: Int = curSettings["AIM_FOV"].toString().toInt(), BONE: Int): MutableList<Any> {
	val retList = mutableListOf(-1.0, 0.0, 0)

	val ePos: Angle = entity.bones(BONE)
	val distance = position.distanceTo(ePos)

	val dest = calculateAngle(me, ePos)

	val pitchDiff = Math.abs(angle.x - dest.x)
	val yawDiff = Math.abs(angle.y - dest.y)
	val fov = Math.abs(Math.sin(Math.toRadians(yawDiff)) * distance)
	val delta = Math.abs((Math.sin(Math.toRadians(pitchDiff)) + Math.sin(Math.toRadians(yawDiff))) * distance)

	if (delta <= lockFOV && delta <= calcClosestDelta) {
		retList[0] = fov
		retList[1] = delta
		retList[2] = entity
	}

	return retList
}

internal fun Entity.inMyTeam() =
		!curSettings["TEAMMATES_ARE_ENEMIES"]!!.strToBool() && if (DANGER_ZONE) {
			me.survivalTeam().let { it > -1 && it == this.survivalTeam() }
		} else me.team() == team()

internal fun Entity.canShoot() = (if (DANGER_ZONE) { true } else { spotted() }
		&& !dormant()
		&& !dead()
		&& !inMyTeam()
		&& !me.dead())

internal inline fun <R> aimScript(duration: Int, crossinline precheck: () -> Boolean,
								  crossinline doAim: (destinationAngle: Angle,
													  currentAngle: Angle, aimSpeed: Int) -> R) = every(duration) {
	if (!precheck()) return@every
	if (!curSettings["ENABLE_AIM"]!!.strToBool()) return@every

	if (!me.weaponEntity().canFire()) {
		reset()
		return@every
	}

	val aim = curSettings["ACTIVATE_FROM_FIRE_KEY"]!!.strToBool() && keyPressed(curSettings["FIRE_KEY"].toString().toInt())
	val forceAim = keyPressed(curSettings["FORCE_AIM_KEY"].toString().toInt())
	val haveAmmo = me.weaponEntity().bullets() > 0

	bone.set(curSettings["AIM_BONE"].toString().toInt())

	val pressed = (aim || forceAim || boneTrig) && !MENUTOG && haveAmmo
	var currentTarget = target.get()

	if (!pressed) {
		reset()
		return@every
	}

	val weapon = me.weapon()
	if (!weapon.pistol && !weapon.automatic && !weapon.shotgun && !weapon.sniper) {
		reset()
		return@every
	}

	val currentAngle = clientState.angle()
	val position = me.position()

	if (currentTarget < 0) {
		currentTarget = findTarget(position, currentAngle, aim)
		if (currentTarget < 0) {
			reset()
			return@every
		}
		target.set(currentTarget)
	}

 	if (currentTarget == me || !currentTarget.canShoot())
	{
		Thread.sleep(500)
		reset()
		return@every
	}
	else {
		val bonePosition = currentTarget.bones(bone.get())

		val destinationAngle = calculateAngle(me, bonePosition)
        if (!perfect.get()) {
            destinationAngle.finalize(currentAngle, (1.1-curSettings["AIM_SMOOTHNESS"].toString().toDouble()/10))
        }
		val aimSpeed = curSettings["AIM_SPEED"].toString().toInt()
		doAim(destinationAngle, currentAngle, aimSpeed)
	}
}