//
//  MainViewController+Sound.h
//
//  Created by Briar Bowser on 05/17/2016.
//

#import "MainViewController+Sound.h"
#import <objc/runtime.h>

@implementation MainViewController (Sound)

@dynamic soundPlugin;

- (void)remoteControlReceivedWithEvent:(UIEvent *)event {
    if ([self.soundPlugin respondsToSelector:@selector(remoteControlReceivedWithEvent:)]) {
        [self.soundPlugin performSelector:@selector(remoteControlReceivedWithEvent:) withObject:event];
    }
}

- (BOOL)canBecomeFirstResponder {
    return YES;
}

- (void)setSoundPlugin:(CDVPlugin *)soundPlugin {
    objc_setAssociatedObject(self, "soundPlugin", soundPlugin, OBJC_ASSOCIATION_ASSIGN);
}

- (CDVPlugin *)soundPlugin {
    return objc_getAssociatedObject(self, "soundPlugin");
}

@end
