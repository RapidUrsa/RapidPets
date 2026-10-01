package com.rapidursa.shoulderpets;

final class EggState
{
    final int speciesId;
    final String speciesName;
    final int combatLevel;
    long remainingMillis;

    EggState(int speciesId, String speciesName, int combatLevel, long remainingMillis)
    {
        this.speciesId = speciesId;
        this.speciesName = speciesName;
        this.combatLevel = combatLevel;
        this.remainingMillis = remainingMillis;
    }

    String encode()
    {
        return "v2:" + speciesId + ":" + combatLevel + ":" + remainingMillis + ":" + speciesName.replace(":", "");
    }

    static EggState decode(String value)
    {
        boolean current = value.startsWith("v2:");
        String[] fields = (current ? value.substring(3) : value).split(":", 4);
        if (fields.length != 4) throw new IllegalArgumentException("Invalid egg state");
        long timer = Long.parseLong(fields[2]);
        // Old builds saved a wall-clock expiry. Preserve only its remaining time.
        if (!current && timer > 0) timer = Math.max(0, timer - System.currentTimeMillis());
        return new EggState(Integer.parseInt(fields[0]), fields[3], Integer.parseInt(fields[1]), timer);
    }
}
