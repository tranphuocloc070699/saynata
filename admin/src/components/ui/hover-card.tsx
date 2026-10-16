import * as HoverCardPrimitive from "@radix-ui/react-hover-card";
import * as React from "react";
import { cn } from "@/lib/utils";

// shadcn/ui HoverCard (Radix). A card that opens when the mouse rests on the trigger; touch never opens it.
export const HoverCard = HoverCardPrimitive.Root;
export const HoverCardTrigger = HoverCardPrimitive.Trigger;

export const HoverCardContent = React.forwardRef<
  React.ComponentRef<typeof HoverCardPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof HoverCardPrimitive.Content>
>(({ className, sideOffset = 8, collisionPadding = 12, ...props }, ref) => (
  <HoverCardPrimitive.Portal>
    <HoverCardPrimitive.Content
      ref={ref}
      sideOffset={sideOffset}
      // The card flips and slides so it stays inside the window.
      collisionPadding={collisionPadding}
      className={cn("z-[60] rounded-md border border-border bg-card p-2 text-card-foreground shadow-lg outline-none", className)}
      {...props}
    />
  </HoverCardPrimitive.Portal>
));
HoverCardContent.displayName = "HoverCardContent";
