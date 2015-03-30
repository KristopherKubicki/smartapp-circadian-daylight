# Circadian Daylight
Use your SmartThings Hub to sync your color changing lights with natural daylight hues

This SmartApp synchronizes your color changing lights with local perceived color temperature of the sky throughout the day.  This gives your environment a more natural feel, with cooler whites during the midday and warmer tints near twilight and dawn.
 
In addition, the SmartApp sets your lights to a nice cool white at 1% in "Sleep" mode, which is far brighter than starlight but won't reset your circadian rhythm or break down too much rhodopsin in your eyes.

![circadian_daylight](https://cloud.githubusercontent.com/assets/478212/6904334/b8decdac-d6e5-11e4-97ec-e48c53a8b96e.png)

Human circadian rhythms are heavily influenced by ambient light levels and hues.  Hormone production, brainwave activity, mood and wakefulness are just some of the cognitive functions tied to cyclical natural light.
 *	http://en.wikipedia.org/wiki/Zeitgeber

The SmartApp is completely dependent on "things happening" in your environment: specifically, motion or switches.  I originally intended for this app to run on a schedule, but SmartThings tends to not publish applications that have an internal scheduler call.  If you want to hack a scheduler into this app, just add "schedule(0 0/5 * * * ?, sunHandler)" into the initialize() routine 

 Here's some further reading:
 * http://www.cambridgeincolour.com/tutorials/sunrise-sunset-calculator.htm
 * http://en.wikipedia.org/wiki/Color_temperature

Technical notes:  I had to make a lot of assumptions when writing this App:
*  The Hue bulbs are only capable of producing a true color spectrum from 2700K to 6000K.  The Hue Pro application indicates the range is a little wider on each side, but I stuck with the Philips documentation.
*  I aligned the color space to CIE with white at D50.  I suspect "true" white for this application might actually be D65, but I will have to recalculate the color temperature if I move it.  
*  There are no considerations for weather or altitude, but does use your Hub's zip code to calculate the sun position.    
*  The app doesn't calculate a true "Blue Hour" -- it just sets the lights to 2700K (warm white) until your hub goes into Night mode

License
-------
Copyright (c) 2015, Kristopher Kubicki
All rights reserved.
