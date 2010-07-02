/**
 * 
 */
package org.jboss.web.tomcat.service.session.persistent;

import org.jboss.web.tomcat.service.session.ClusteredSession;
import org.jboss.web.tomcat.service.session.OutdatedSessionChecker;
import org.jboss.web.tomcat.service.session.distributedcache.spi.OutgoingDistributableSessionData;

/**
 *
 *
 * @author Brian Stansberry
 * 
 * @version $Revision: $
 */
public class AlwaysTrueOutdatedSessionChecker
      implements  OutdatedSessionChecker
{
   public boolean isSessionOutdated(ClusteredSession<? extends OutgoingDistributableSessionData> session)
   {
      return true;
   }

}
