/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.tom.util;

import navigators.smart.tom.core.messages.TOMMessage;

/**
 * Provides support for building custom response extractors to be used in the
 * ServiceProxy.
 *
 * @author alysson
 */
public interface Extractor {
    TOMMessage extractResponse(TOMMessage[] replies, int sameContent, int lastReceived);
}
