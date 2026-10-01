package com.rapidursa.shoulderpets;

final class PetPlacement
{
    final int sideways, height, forward, size;

    PetPlacement(int sideways, int height, int forward, int size)
    {
        this.sideways = Math.max(-256, Math.min(256, sideways));
        this.height = Math.max(-256, Math.min(256, height));
        this.forward = Math.max(-256, Math.min(256, forward));
        this.size = Math.max(10, Math.min(100, size));
    }

    static PetPlacement defaults() { return new PetPlacement(42, 0, 0, 20); }

    String encode() { return sideways + "," + height + "," + forward + "," + size; }

    static PetPlacement decode(String value)
    {
        if (value == null) return null;
        String[] fields = value.split(",");
        if (fields.length != 4) return null;
        try
        {
            return new PetPlacement(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
        }
        catch (NumberFormatException ex) { return null; }
    }
}
