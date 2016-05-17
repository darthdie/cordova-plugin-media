//
//  MainViewController+Sound.h
//
//  Created by Briar Bowser on 05/17/2016.
//

#import "MainViewController+Sound.h"

@implementation MainViewController (Sound)

- (void)remoteControlReceivedWithEvent:(UIEvent *)receivedEvent {
    [[NSNotificationCenter defaultCenter] postNotificationName:@"receivedEvent" object:receivedEvent];
}

@end
