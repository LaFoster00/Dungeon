package Dungeon;

import Intrinsic;

public class Arrays {
  @Intrinsic("Dungeon.Arrays.copyof(Object[], int)")
  public static native <T> T[] copyOf(T[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyof(byte[], int)")
  public static native byte[] copyOf(byte[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyof(short[], int)")
  public static native short[] copyOf(short[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyof(char[], int)")
  public static native char[] copyOf(char[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyof(int[], int)")
  public static native int[] copyOf(int[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyof(long[], int)")
  public static native long[] copyOf(long[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyof(float[], int)")
  public static native float[] copyOf(float[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyof(double[], int)")
  public static native double[] copyOf(double[] original, int newLength);
}
