package components;

/**
 * Marker interface for components that are managed by the VM. These are components that are added
 * by the VM and depend on the VM to remove them again. These components might need to be removed
 * manually if the vm is stopped early and therefore need to be specially marked.
 *
 * @see HeroActionComponent.Move
 */
public interface VmManagedComponent {
  void destroyVmManagedComponent();
}
