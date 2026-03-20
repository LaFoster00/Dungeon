package Dungeon;

import Intrinsic;

public class Arrays {
  @Intrinsic("Dungeon.Arrays.copyOf(Object[], int)")
  public static native <T> T[] copyOf(T[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyOf(byte[], int)")
  public static native byte[] copyOf(byte[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyOf(short[], int)")
  public static native short[] copyOf(short[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyOf(char[], int)")
  public static native char[] copyOf(char[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyOf(int[], int)")
  public static native int[] copyOf(int[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyOf(long[], int)")
  public static native long[] copyOf(long[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyOf(float[], int)")
  public static native float[] copyOf(float[] original, int newLength);

  @Intrinsic("Dungeon.Arrays.copyOf(double[], int)")
  public static native double[] copyOf(double[] original, int newLength);
}
