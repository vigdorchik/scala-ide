package scala.tools.eclipse.ext


import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import org.osgi.util.tracker.ServiceTrackerCustomizer

/**
 * Interface to be implemented by services who want to be notified when the Scala Plugin start.
 * Use this interface to avoid registration of a BundleListener
 * @TODO find a better way to hook.
 */
trait OnStart {
  def started(bc : BundleContext)
}

class ServiceTrackerCustomizer4OnStart(val context :BundleContext) extends ServiceTrackerCustomizer {

  def addingService(sr : ServiceReference) : Object = {
    println("addingService : " + sr)
    val service = context.getService(sr).asInstanceOf[OnStart]
    println("service : " + service)
    if (service != null) {
      service.started(context)
    }
    context.ungetService(sr)
    service
  }

  def modifiedService(sr : ServiceReference, s : Object) {
    // removedService(reference, service);
    // addingService(reference);
  }

  def removedService(sr : ServiceReference, s : Object) {
  }

}