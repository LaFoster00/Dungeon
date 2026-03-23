package Dungeon;

public class Arrays {
  /**
   * Copies the specified array, truncating or padding with nulls (if necessary)
   * so the copy has the specified length.
   *
   * @param original  the array to be copied
   * @param newLength the length of the copy to be returned
   * @return a copy of the original array, truncated or padded with nulls to
   *         obtain the specified length
   */
  @Intrinsic("Dungeon.Arrays.copyOf(Object[], int)")
  public static native <T> T[] copyOf(T[] original, int newLength);

  /**
   * Copies the specified array, truncating or padding with the default value (if
   * necessary) so the copy has the specified length.
   * 
   * @param original  the array to be copied
   * @param newLength the length of the copy to be returned
   * @return a copy of the original array, truncated or padded with the default
   *         value to obtain the specified length
   */
  @Intrinsic("Dungeon.Arrays.copyOf(byte[], int)")
  public static native byte[] copyOf(byte[] original, int newLength);

  /**
   * Copies the specified array, truncating or padding with the default value (if
   * necessary) so the copy has the specified length.
   * 
   * @param original  the array to be copied
   * @param newLength the length of the copy to be returned
   * @return a copy of the original array, truncated or padded with the default
   *         value to obtain the specified length
   */
  @Intrinsic("Dungeon.Arrays.copyOf(short[], int)")
  public static native short[] copyOf(short[] original, int newLength);

  /**
   * Copies the specified array, truncating or padding with the default value (if
   * necessary) so the copy has the specified length.
   * 
   * @param original  the array to be copied
   * @param newLength the length of the copy to be returned
   * @return a copy of the original array, truncated or padded with the default
   *         value to obtain the specified length
   */
  @Intrinsic("Dungeon.Arrays.copyOf(char[], int)")
  public static native char[] copyOf(char[] original, int newLength);

  /**
   * Copies the specified array, truncating or padding with the default value (if
   * necessary) so the copy has the specified length.
   * 
   * @param original  the array to be copied
   * @param newLength the length of the copy to be returned
   * @return a copy of the original array, truncated or padded with the default
   *         value to obtain the specified length
   */
  @Intrinsic("Dungeon.Arrays.copyOf(int[], int)")
  public static native int[] copyOf(int[] original, int newLength);

  /**
   * Copies the specified array, truncating or padding with the default value (if
   * necessary) so the copy has the specified length.
   * 
   * @param original  the array to be copied
   * @param newLength the length of the copy to be returned
   * @return a copy of the original array, truncated or padded with the default
   *         value to obtain the specified length
   */
  @Intrinsic("Dungeon.Arrays.copyOf(long[], int)")
  public static native long[] copyOf(long[] original, int newLength);

  /**
   * Copies the specified array, truncating or padding with the default value (if
   * necessary) so the copy has the specified length.
   * 
   * @param original  the array to be copied
   * @param newLength the length of the copy to be returned
   * @return a copy of the original array, truncated or padded with the default
   *         value to obtain the specified length
   */
  @Intrinsic("Dungeon.Arrays.copyOf(float[], int)")
  public static native float[] copyOf(float[] original, int newLength);

  /**
   * Copies the specified array, truncating or padding with the default value (if
   * necessary) so the copy has the specified length.
   * 
   * @param original  the array to be copied
   * @param newLength the length of the copy to be returned
   * @return a copy of the original array, truncated or padded with the default
   *         value to obtain the specified length
   */
  @Intrinsic("Dungeon.Arrays.copyOf(double[], int)")
  public static native double[] copyOf(double[] original, int newLength);
}
