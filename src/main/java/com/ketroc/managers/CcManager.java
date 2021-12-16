package com.ketroc.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.geometry.Position;
import com.ketroc.micro.*;
import com.ketroc.models.*;
import com.ketroc.purchases.Purchase;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.purchases.PurchaseStructureMorph;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.strategies.defenses.CannonRushDefense;
import com.ketroc.utils.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CcManager {
    private static boolean isMuleSpamming;

    public static void onStep() {
        //keep CCs active (make scvs, morph ccs, call mules)
        ccActivityLogic();

        //spam mules on opponent
        spamMulesOnEnemyBase();

        //turn low health expansion command centers into macro OCs
        saveDyingCCs();

        //build command center logic
        if (!Strategy.EXPAND_SLOWLY || Time.nowFrames() > Time.toFrames("5:00")) {
            buildCCLogic();
        }
    }

    private static void spamMulesOnEnemyBase() {
        //exit since mule spam replaced with troll muling
        if (MuleMessages.doTrollMule) {
            return;
        }
        List<Unit> ocList = UnitUtils.getMyUnitsOfType(Units.TERRAN_ORBITAL_COMMAND);
        int ocCount = (int)ocList.stream().filter(oc -> oc.getEnergy().get() >= 50f).count(); //OCs with mule energy
        int numMulesAvailable = ocList.stream()
                .mapToInt(oc -> oc.getEnergy().orElse(0f).intValue() / 50)
                .sum();

        //if ocs has energy pooled up and scan is needed to mule enemy
        if (isMuleSpamming || numMulesAvailable > 30 || ocList.stream().anyMatch(oc -> oc.getEnergy().orElse(0f) >= 199)) {
            isMuleSpamming = true;
            for (Base base : GameCache.baseList) {
                if (isBaseReadyForMuleSpam(base)) {
                    for (Unit mineralNode : base.getMineralPatchUnits()) {
                        ActionHelper.unitCommand(ocList, Abilities.EFFECT_CALL_DOWN_MULE, mineralNode, false);
                        ActionHelper.unitCommand(ocList, Abilities.EFFECT_CALL_DOWN_MULE, mineralNode, false);
                        numMulesAvailable -= 2;
                        ocCount -= 2;
                        if (numMulesAvailable <= 0) {
                            isMuleSpamming = false;
                            return;
                        }
                        if (ocCount <= 0) {
                            return;
                        }
                    }
                    base.prevMuleSpamFrame = Time.nowFrames();
                }
            }
            if (numMulesAvailable > 4) {
                if (!scanNextBase()) {
                    isMuleSpamming = muleMyBases(ocList, ocCount, numMulesAvailable);
                }
            }
            else {
                isMuleSpamming = false;
            }
        }
    }

    private static boolean muleMyBases(List<Unit> ocList, int ocCount, int numMulesAvailable) {
        for (int i=GameCache.baseList.size()-1; i >= 0; i--) {
            Base base = GameCache.baseList.get(i);
            if (base.isReadyForMining() && base.prevMuleSpamFrame + Time.toFrames(64) < Time.nowFrames()) {
                for (Unit mineral : base.getMineralPatchUnits()) {
                    base.prevMuleSpamFrame = Time.nowFrames();
                    ActionHelper.unitCommand(ocList, Abilities.EFFECT_CALL_DOWN_MULE, mineral, false);
                    ActionHelper.unitCommand(ocList, Abilities.EFFECT_CALL_DOWN_MULE, mineral, false);
                    ocCount -= 2;
                    numMulesAvailable -= 2;
                    if (numMulesAvailable <= 0) {
                        return false;
                    }
                    if (ocCount <= 0) {
                        return true;
                    }
                }
            }
        }
        MuleMessages.doTrollMule = true;
        return true;
    }

    //checks if base is enemy owned, has minerals, is being scanned, and has no mules
    private static boolean isBaseReadyForMuleSpam(Base base) {
        return !base.isMyBase() &&
                !base.getMineralPatchUnits().isEmpty() &&
                isMineralsVisible(base.getMineralPatchUnits()) &&
                base.prevMuleSpamFrame + Time.toFrames(10) < Time.nowFrames();
    }

    private static boolean scanNextBase() {
        //scan was cast on previous frame
        if (Time.nowFrames() <= ArmyManager.prevScanFrame + 24) {
            return true;
        }
        Base nextBase = GameCache.baseList.stream()
                .filter(base -> !base.isMyBase() &&
                        base.prevMuleSpamFrame + Time.toFrames(30) < Time.nowFrames() &&
                        !base.getMineralPatchUnits().isEmpty() &&
                        !isMineralsVisible(base.getMineralPatchUnits()))
                .findFirst()
                .orElse(null);
        if (nextBase == null) {
            return false;
        }
        UnitUtils.scan(nextBase.getCcPos());
        return true;
    }

    private static void ccActivityLogic() {
        for (Unit cc : GameCache.ccList) {
            if (cc.getBuildProgress() == 1.0f && UnitUtils.getOrder(cc) == null) {
                switch ((Units) cc.getType()) {
                    case TERRAN_COMMAND_CENTER:
                        if (ccToBeOC(cc.getPosition().toPoint2d())) {
                            if (UnitUtils.numMyUnits(UnitUtils.ORBITAL_COMMAND_TYPE, true) >= Strategy.MAX_OCS) {
                                Point2d expansionBasePos = getNextAvailableExpansionPosition();
                                if (expansionBasePos != null) {
                                    floatCCForExpansion(cc, expansionBasePos);
                                }
                                else {
                                    //send to a random enemy base
                                    expansionBasePos = UnitUtils.getRandomUnownedBasePos();
                                    if (expansionBasePos != null) {
                                        if (GameCache.gasBank > 1500 && UnitMicroList.numOfUnitClass(StructureFloaterExpansionCC.class)
                                                < Base.numEnemyBases() * 2) {
                                            floatCCForPfHarass(cc, expansionBasePos);
                                        }
                                        else if (!Purchase.isMorphQueued(Abilities.MORPH_ORBITAL_COMMAND)) {
                                            KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, cc));
                                        }
                                    }
                                }
                            }
                            //float CC from danger if it isn't a base CC
                            else if (InfluenceMaps.getGroundThreatToStructure(cc) * 2 > InfluenceMaps.getAirThreatToStructure(cc) &&
                                    GameCache.baseList.stream().noneMatch(base -> UnitUtils.getDistance(cc, base.getCcPos()) < 2)) {
                                UnitMicroList.add(new StructureFloater(cc));
                            }
                            else if (!PurchaseStructureMorph.isTechRequired(Abilities.MORPH_ORBITAL_COMMAND)) {
                                //TODO: handle logic of Strategy.PRIORITIZE_EXPANDING here
                                //if not main cc, and if needed for expansion
                                if (UnitUtils.getDistance(cc, LocationConstants.baseLocations.get(0)) > 1 &&
                                        !Base.isABasePos(cc.getPosition().toPoint2d()) &&
                                        isCcNeededForExpansion()) {
                                    Point2d nextFreeBasePos = getNextAvailableExpansionPosition();
                                    if (nextFreeBasePos == null) { //do nothing, waits for expansion to free up TODO: make OC or wait??
                                        if (!Purchase.isMorphQueued(Abilities.MORPH_ORBITAL_COMMAND)) {
                                            KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, cc));
                                        }
                                    }
                                    else {
                                        floatCCForExpansion(cc, nextFreeBasePos);
                                    }
                                }
                                else if (!Purchase.isMorphQueued(Abilities.MORPH_ORBITAL_COMMAND)) {
                                    KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, cc));
                                }
                                break; //don't queue scv
                            }
                        }
                        else { //if base that will become a PF TODO: use same logic as OC
                            if (!PurchaseStructureMorph.isTechRequired(Abilities.MORPH_PLANETARY_FORTRESS)) {
                                if (!Purchase.isMorphQueued(Abilities.MORPH_PLANETARY_FORTRESS)) {
                                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_PLANETARY_FORTRESS, cc));
                                    break; //don't queue scv
                                }
                            }
                        }
                        //build scv
                        if (Bot.OBS.getMinerals() >= 50 &&
                                UnitUtils.numScvs(true) < Math.min(Base.scvsReqForMyBases() + 10, Strategy.maxScvs)) {
                            ActionHelper.unitCommand(cc, Abilities.TRAIN_SCV, false);
                            Cost.updateBank(Units.TERRAN_SCV);
                        }
                        break;
                    case TERRAN_ORBITAL_COMMAND:
                        //float OC from danger if it isn't my main base OC
                        if (InfluenceMaps.getGroundThreatToStructure(cc) * 2 > InfluenceMaps.getAirThreatToStructure(cc) &&
                                GameCache.baseList.stream().noneMatch(base -> UnitUtils.getDistance(cc, base.getCcPos()) < 2)) {
                            UnitMicroList.add(new StructureFloater(cc));
                        }
                        else if (cc.getEnergy().get() >= 50) {
                            //scan enemy main at 4:30
                            if (LocationConstants.opponentRace == Race.PROTOSS &&
                                    Strategy.gamePlan != GamePlan.MARINE_RUSH &&
                                    Strategy.gamePlan != GamePlan.SCV_RUSH &&
                                    !Switches.scoutScanComplete && Time.nowFrames() > Time.toFrames("4:30")) {
                                ActionHelper.unitCommand(cc, Abilities.EFFECT_SCAN,
                                        Position.towards(LocationConstants.enemyMainBaseMidPos, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1), 3), false);
                                Switches.scoutScanComplete = true;
                            }
                            else if (!MuleMessages.doTrollMule &&
                                    GameCache.mineralBank < 3000 &&
                                    !Switches.hasCastOCSpellThisFrame &&
                                    UnitUtils.numScansAvailable() > Switches.numScansToSave) {
                                //calldown mule
                                boolean didMule = false;
                                for (int i = GameCache.baseList.size() - 1; i >= 0; i--) {
                                    Base base = GameCache.baseList.get(i);
                                    if (base.isReadyForMining()) {
                                        int numMules = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_MULE, base.getCcPos(), 10).size();
                                        if (numMules < base.getMineralPatchUnits().size()) {
                                            Unit mineralToMule;
                                            if (i == 2 && LocationConstants.MAP.contains("Golden Wall")) { //special case so mules don't get trapped
                                                mineralToMule = base.getMineralPatches().stream()
                                                        .map(MineralPatch::getNode)
                                                        .min(Comparator.comparing(unit -> UnitUtils.getDistance(unit, base.getCcPos())))
                                                        .orElse(null);
                                            }
                                            else { //mine the largest patch
                                                mineralToMule = base.getMineralPatches().stream()
                                                        .map(mineralPatch -> mineralPatch.getNode())
                                                        .max(Comparator.comparing(mineral -> mineral.getMineralContents().orElse(0)))
                                                        .orElse(null);
                                            }
                                            if (mineralToMule != null) {
                                                ActionHelper.unitCommand(cc, Abilities.EFFECT_CALL_DOWN_MULE, mineralToMule, false);
                                                didMule = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                //if no base minerals, then distance mule closest mineral patch
                                if (!didMule) {
                                    Bot.OBS.getUnits(Alliance.NEUTRAL, node -> UnitUtils.MINERAL_NODE_TYPE.contains(node.unit().getType()) &&
                                                    node.unit().getDisplayType() == DisplayType.VISIBLE)
                                            .stream()
                                            .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), cc)))
                                            .map(UnitInPool::unit)
                                            .ifPresent(nearestMineral -> {
                                                ActionHelper.unitCommand(cc, Abilities.EFFECT_CALL_DOWN_MULE, nearestMineral, false);
                                            });
                                }
                                Switches.hasCastOCSpellThisFrame = true;
                            }
                        }
                        //no break
                    case TERRAN_PLANETARY_FORTRESS:
                        //build scv
                        if (UnitUtils.numScvs(true) < Math.min(Base.scvsReqForMyBases() + 10, Strategy.maxScvs)) {
                            ActionHelper.unitCommand(cc, Abilities.TRAIN_SCV, false);
                            Cost.updateBank(Units.TERRAN_SCV);
                        }
                        break;
                }
            }
        }
    }

    private static void floatCCForPfHarass(Unit cc, Point2d basePos) {
        floatCCToBase(cc, basePos, true);
    }

    private static void floatCCForExpansion(Unit cc, Point2d basePos) {
        floatCCToBase(cc, basePos, false);
    }

    private static void floatCCToBase(Unit cc, Point2d basePos, boolean isEnemyBase) {
        UnitMicroList.add(new StructureFloaterExpansionCC(cc, basePos));
        LocationConstants.MACRO_OCS.add(cc.getPosition().toPoint2d());

        //setCC in baseList
        if (!isEnemyBase) {
            GameCache.baseList.stream()
                    .filter(base -> base.getCcPos().distance(basePos) < 1)
                    .findFirst()
                    .ifPresent(base -> base.setCc(Bot.OBS.getUnit(cc.getTag())));
        }

        //remove OC morph from purchase queue
        PurchaseStructureMorph.remove(cc.getTag());
    }

    private static boolean isCcNeededForExpansion() {
        //if safe and oversaturated
        return !UnitUtils.isWallUnderAttack() && CannonRushDefense.isSafe && (
                WorkerManager.numScvsPerGas == 3 ||
                        Base.scvsReqForMyBases() < Math.min(Strategy.maxScvs, UnitUtils.numScvs(true) + 5)
        );
    }

    private static void saveDyingCCs() {
        //skip if already maxed on macro OCs
        if (LocationConstants.MACRO_OCS.isEmpty()) {
            return;
        }
        //loop through bases looking for a dying cc
        for (Base base : GameCache.baseList) {
            if (!base.isMyBase()) {
                continue;
            }
            Unit cc = base.getCc().unit();

            //if complete CC or incomplete PF, low health, and ground attacking enemy nearby
            if (cc.getType() == Units.TERRAN_COMMAND_CENTER && cc.getBuildProgress() == 1.0f && UnitUtils.getHealthPercentage(cc) < Strategy.floatBaseAt
                    && !Bot.OBS.getUnits(Alliance.ENEMY, u -> UnitUtils.getDistance(u.unit(), cc) <= 10 && UnitUtils.canAttackGround(u.unit())).isEmpty()) {
                if (ActionIssued.getCurOrder(base.getCc()).isEmpty() && !LocationConstants.MACRO_OCS.isEmpty()) {
                    FlyingCC.addFlyingCC(cc, LocationConstants.MACRO_OCS.remove(0), true);

                    //remove cc from base
                    base.setCc(null);

                    //cancel PF morph in purchase queue
                    for (int i = 0; i < KetrocBot.purchaseQueue.size(); i++) {
                        Purchase p = KetrocBot.purchaseQueue.get(i);
                        if (p instanceof PurchaseStructureMorph) {
                            if (((PurchaseStructureMorph) p).getProductionStructure().getTag().equals(cc.getTag())) {
                                KetrocBot.purchaseQueue.remove(i);
                                break;
                            }
                        }
                    }
                }
                //cancel PF upgrade
                else if (UnitUtils.getOrder(cc) == Abilities.MORPH_PLANETARY_FORTRESS) {
                    ActionHelper.unitCommand(cc, Abilities.CANCEL_MORPH_PLANETARY_FORTRESS, false);
                }
                //cancel scv production
                else {
                    ActionHelper.unitCommand(cc, Abilities.CANCEL_LAST, false);
                }
            }
        }
//        //send flying CCs to macro OC location
//        List<Unit> flyingCCs = GameState.allFriendliesMap.getOrDefault(Units.TERRAN_COMMAND_CENTER_FLYING, new ArrayList<>());
//        for (Unit cc : flyingCCs) {
//            //if not on the way to land already
//            if (ActionIssued.getCurOrder(cc).isEmpty()) {
//                ActionHelper.unitCommand(cc, Abilities.LAND, LocationConstants.MACRO_OCS.remove(LocationConstants.MACRO_OCS.size()-1), false);
//            }
//            //Bot.onUnitDestroyed() re-adds this position to MACRO_OCS if the flying cc dies
//        }
    }

    private static void buildCCLogic() {
        //purchase new CCs at 500minerals unless nearing full saturation (in which case 400mins)
        int mineralsRequired = 500;
        if (UnitUtils.numStructuresProducingOrQueued(Units.TERRAN_COMMAND_CENTER) == 0 &&
                UnitUtils.numScvs(true) >= Math.min(Strategy.maxScvs,
                        Base.scvsReqForMyBases() - (4 * UnitUtils.numMyUnits(UnitUtils.COMMAND_CENTER_TYPE, false)))) {
            mineralsRequired = 400;
        }

        if (GameCache.mineralBank > mineralsRequired && !Purchase.isStructureQueued(Units.TERRAN_COMMAND_CENTER) &&
                (Base.numMyBases() < LocationConstants.baseLocations.size() - Strategy.NUM_DONT_EXPAND ||
                        !LocationConstants.MACRO_OCS.isEmpty() ||
                        !Placement.possibleCcPosList.isEmpty())) {
            if ((GameCache.mineralBank > GameCache.gasBank && GameCache.gasBank > 2000) ||
                    Base.numAvailableBases() > 0 ||
                    UnitUtils.numMyUnits(UnitUtils.ORBITAL_COMMAND_TYPE, true) < Strategy.MAX_OCS) {
                addCCToPurchaseQueue();
            }
        }
    }

    private static void addCCToPurchaseQueue() {
        if (Strategy.BUILD_EXPANDS_IN_MAIN) {
            if (!purchaseMacroCC()) {
                if (!purchaseExpansionCC()) {
                    purchaseExtraCC();
                }
            }
        }
        else if (Strategy.PRIORITIZE_EXPANDING) {
            if (!purchaseExpansionCC()) {
                if (!purchaseMacroCC()) {
                    purchaseExtraCC();
                }
            }
        }
        else {
            int scvsForMaxSaturation = Base.scvsReqForMyBases();
            int numScvs = UnitUtils.numScvs(true);
            if (UnitUtils.isWallUnderAttack() || !CannonRushDefense.isSafe) {
                purchaseMacroCC();
            } else if (Math.min(numScvs + 25, Strategy.maxScvs) <= scvsForMaxSaturation) {
                if (!purchaseMacroCC()) {
                    if (!purchaseExpansionCC()) {
                        purchaseExtraCC();
                    }
                }
            } else {
                if (!purchaseExpansionCC()) {
                    if (!purchaseMacroCC()) {
                        purchaseExtraCC();
                    }
                }
            }
        }
    }

    private static void purchaseExtraCC() {
        if (GameCache.mineralBank > 2000 && enemyHasMineralPatches()) {
            Point2d ccPos = Placement.getNextExtraCCPos();
            if (ccPos != null) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, ccPos));
            }
        }
    }

    private static boolean enemyHasMineralPatches() {
        return GameCache.baseList.stream()
                .filter(base -> !base.isMyBase())
                .anyMatch(base -> !base.isMyBase() &&
                        base.lastScoutedFrame + Time.toFrames("5:00") > Time.nowFrames() &&
                        !base.getMineralPatchUnits().isEmpty());
    }

    private static boolean purchaseExpansionCC() {
        //if an expansion position is available, build expansion CC
        Point2d expansionPos = getNextAvailableExpansionPosition();
        if (expansionPos != null) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, expansionPos));
        }
        return expansionPos != null;
    }

    public static Point2d getNextAvailableExpansionPosition() {
        List<Base> expansionOptions = GameCache.baseList.subList(0, GameCache.baseList.size() - getNumEnemyBasesIgnored()).stream()
                .filter(base -> base.isUntakenBase() &&
                        !base.isDriedUp() &&
                        InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, base.getCcPos()) == 0 &&
                        base.isReachable())
                .collect(Collectors.toList());

        for (Base base : expansionOptions) {
            if (Bot.QUERY.placement(Abilities.BUILD_COMMAND_CENTER, base.getCcPos())) {
                return base.getCcPos();
            }
            else if (!ExpansionClearing.isVisiblyBlockedByUnit(base.getCcPos())) { //UnitUtils.isExpansionCreepBlocked(base.getCcPos())
                ExpansionClearing.add(base.getCcPos());
            }
        }
        return null;
    }

    public static int getNumEnemyBasesIgnored() {
        return (LocationConstants.MACRO_OCS.isEmpty()) ? 2 : 5; //try to expand deeper on enemy side when macro OCs are complete
    }

    public static boolean purchaseMacroCC() {
        if (LocationConstants.MACRO_OCS.isEmpty()) {
            return false;
        }

        Point2d ccPos;
        Point2d nextAvailableBase = Base.getNextAvailableBase();
        if (nextAvailableBase == null) {
            ccPos = LocationConstants.MACRO_OCS.remove(0);
        }
        else {
            ccPos = LocationConstants.MACRO_OCS.stream()
                    .min(Comparator.comparing(p -> p.distance(nextAvailableBase)))
                    .get();
            LocationConstants.MACRO_OCS.remove(ccPos);
        }
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, ccPos));
        return true;
    }

    public static boolean ccToBeOC(Point2d ccPos) {
        return LocationConstants.baseLocations
                .subList(Strategy.NUM_BASES_TO_OC, LocationConstants.baseLocations.size()) //ignore OC base locations
                .stream()
                .noneMatch(p -> ccPos.distance(p) < 1);
    }

    private static boolean isMineralsVisible(List<Unit> mineralPatches) {
        return mineralPatches.stream().allMatch(patch -> patch.getDisplayType() == DisplayType.VISIBLE);
    }

}
