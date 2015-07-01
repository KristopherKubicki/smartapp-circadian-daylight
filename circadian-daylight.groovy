/**
 *  Circadian Daylight 1.5
 *
 *  This SmartApp synchronizes your color changing lights with local perceived color  
 *     temperature of the sky throughout the day.  This gives your environment a more 
 *     natural feel, with cooler whites during the midday and warmer tints near twilight 
 *     and dawn.  
 * 
 *  In addition, the SmartApp sets your lights to a nice cool white at 1% in 
 *     "Sleep" mode, which is far brighter than starlight but won't reset your
 *     circadian rhythm or break down too much rhodopsin in your eyes.
 *
 *  Human circadian rhythms are heavily influenced by ambient light levels and 
 * 	hues.  Hormone production, brainwave activity, mood and wakefulness are 
 * 	just some of the cognitive functions tied to cyclical natural light.
 *	http://en.wikipedia.org/wiki/Zeitgeber
 *
 *  The SmartApp is completely dependent on "things happening" in your environment:
 *     specifically, motion or switches.  I originally intended for this app to run
 *     on a schedule, but SmartThings tends to not publish applications that have 
 *     an internal scheduler call.  If you want to hack a scheduler into this app, 
 *     just add "schedule(0 0/5 * * * ?, sunHandler)" into the initialize() routine 
 *  
 *  Here's some further reading:
 * 
 * http://www.cambridgeincolour.com/tutorials/sunrise-sunset-calculator.htm
 * http://en.wikipedia.org/wiki/Color_temperature
 * 
 *  Technical notes:  I had to make a lot of assumptions when writing this app
 *     *  The Hue bulbs are only capable of producing a true color spectrum from
 *		2700K to 6000K.  The Hue Pro application indicates the range is 
 *		a little wider on each side, but I stuck with the Philips 
 * 		documentation
 *     *  I aligned the color space to CIE with white at D50.  I suspect "true"
 *		white for this application might actually be D65, but I will have
 *		to recalculate the color temperature if I move it.  
 *     *  There are no considerations for weather or altitude, but does use your 
 *		hub's zip code to calculate the sun position.    
 *     *  The app doesn't calculate a true "Blue Hour" -- it just sets the lights to
 *		2700K (warm white) until your hub goes into Night mode
 *
 *  Version 1.5: June 26, 2015 - Merged with SANdood's optimizations, breaks unofficial LIGHTIFY support
 *  Version 1.4: May 21, 2015 - Clean up mode handling
 *  Version 1.3: April 8, 2015 - Reduced Hue IO, increased robustness
 *  Version 1.2: April 7, 2015 - Add support for LIGHTIFY bulbs, dimmers and user selected "Sleep"
 *  Version 1.1: April 1, 2015 - Add support for contact sensors 
 *  Version 1.0: March 30, 2015 - Initial release
 *  
 *  The latest version of this file can be found at
 *     https://github.com/KristopherKubicki/smartapp-circadian-daylight/
 *   
 */

definition(
	name: "Circadian Daylight",
	namespace: "KristopherKubicki",
	author: "kristopher@acm.org",
	description: "Sync your color changing lights with natural daylight hues",
	category: "Green Living",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/MiscHacking/mindcontrol.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/MiscHacking/mindcontrol@2x.png"
)

preferences {
	section("When these switches turn on...") {
		input "switches", "capability.switch", title: "Which switches?", multiple:true, required: false
	}
	section("Or these motion sensors are activated...") {
		input "motions", "capability.motionSensor", title: "Which motions?", multiple:true, required: false
	}
    section("Or these contact sensors are opened...") {
		input "contacts", "capability.contactSensor", title: "Which contacts?", multiple:true, required: false
	}
	section("Control these Color Changing bulbs...") {
		input "bulbs", "capability.colorControl", title: "Which Color Changing Bulbs?", multiple:true, required: false
	}
    section("Control these Temperature Changing bulbs...") {
		input "cbulbs", "capability.switchLevel", title: "Which Temperature Changing Bulbs?", multiple:true, required: false
	}
    section("Control these Dimming bulbs...") {
		input "dimmers", "capability.switchLevel", title: "Which Dimming Bulbs?", multiple:true, required: false
	}
    section("What are your 'Sleep' modes?") {
		input "smodes", "mode", title: "What are your Sleep modes?", multiple:true, required: false
	}
}


def installed() {
	unsubscribe()
    unschedule()
	initialize()
}

def updated() {
	unsubscribe()
    unschedule()
	initialize()
}

private def initialize() {
	log.debug("initialize() with settings: ${settings}")
	subscribe(switches, "switch", switchHandler)
    
	subscribe(motions, "motion", sunHandler)
    subscribe(contacts, "contact", sunHandler)
	subscribe(dimmers, "switch.on", sunHandler)
    subscribe(cbulbs, "switch.on", sunHandler)
    subscribe(bulbs, "switch.on", sunHandler)
    
	subscribe(location, "mode", modeHandler)
}

// If we detect the Hub moving into a sleep mode, also activate the handler
def modeHandler(evt) {

	def success = 0
	for (smode in smodes) { 
    	if(location.mode == smode) { 
        	success == 1
        	sunHandler(evt)
        }
	}
    if(success == 0) { 
    	for(dimmer in dimmers) { 
        	if(dimmer.currentValue("switch") == "on") { 
    			dimmer.setLevel(100)
            }
        }
        for(bulb in bulbs) { 
        	if(bulb.currentValue("switch") == "on") { 
        		bulb.setLevel(100)
            }
        }
        for(cbulb in cbulbs) {
        	if(cbulb.currentValue("switch") == "on") { 
        		cbulb.setLevel(100)
            }
        }
    }
}

// wait for bulbs to turn on
def switchHandler(evt) {
	runIn(1,sunHandler,[overwrite: false])
    runIn(3,sunHandler,[overwrite: false])
    runIn(5,sunHandler,[overwrite: false])
    runIn(10,sunHandler,[overwrite: false])
}

def sunHandler(evt) {
//	log.debug "$evt.name: $evt.value"

	def after = getSunriseAndSunset()
	def midDay = after.sunrise.time + ((after.sunset.time - after.sunrise.time) / 2)

	def currentTime = now()
    
	def int colorTemp = 2700    
	if(currentTime < after.sunrise.time) {
//		log.debug("this is early twilight")
		colorTemp = 2700
	}
	else if(currentTime > after.sunset.time) { 
//		log.debug("this is late twilight")
		colorTemp = 2700
	}
	else {
//    	log.debug("this is daylight")
		if(currentTime < midDay) { 
			colorTemp = 2700 + ((currentTime - after.sunrise.time) / (midDay - after.sunrise.time) * 3300)
//			log.debug("this is morning: $colorTemp")
		}
		else { 
			colorTemp = 6000 - ((currentTime - midDay) / (after.sunset.time - midDay) * 3300)
//            log.debug("this is afternoon: $colorTemp")
		}
	}
    def hsb = rgbToHSB(ctToRGB(colorTemp))
	
    for (smode in smodes) {
		if(location.mode == smode) { 	
//			log.debug("this is starlight")
			colorTemp = 6000
			hsb = rgbToHSB(ctToRGB(colorTemp))
			hsb.b = 1
        
        	for(dimmer in dimmers) { 
            	if(dimmer.currentValue("switch") == "on") { 
        			dimmer.setLevel(1)
                }
            }
            for(cbulb in cbulbs) { 
            	if(cbulb.currentValue("switch") == "on") {
                	cbulb.setLevel(1)
                }
            }
        }
	}
    
    for(cbulb in cbulbs) { 
    	if(cbulb.currentValue("switch") == "on") {
			cbulb.setColorTemperature(colorTemp)
        }
    }

    hsb.h = hsb.h as Integer
    hsb.s = hsb.s as Integer
    hsb.b = hsb.b as Integer ?: 1
 			
 	def newValue = [hue: Math.round(hsb.h) as Integer, saturation: Math.round(hsb.s) as Integer, level: Math.round(hsb.b) as Integer ?: 1]
    for(bulb in bulbs) { 
    	if(bulb.currentValue("switch") == "on") {
			bulb.setColor(newValue) 
        }
    }
}

// Based on color temperature converter from 
//  http://www.tannerhelland.com/4435/convert-temperature-rgb-algorithm-code/
// As commented, this will not work for color temperatures below 2700 or above 6000
def ctToRGB(ct) { 

	if(ct < 2700) { ct = 2700 }
    if(ct > 6000) { ct = 6000 }

	ct = ct / 100
	def r = 255
	def g = 99.4708025861 * Math.log(ct) - 161.1195681661
	def b = 138.5177312231 * Math.log(ct - 10) - 305.0447927307

//	log.debug("r: $r g: $g b: $b")

	def rgb = [:]
	rgb = [r: r, g: g, b: b] 
	rgb
}

// Based on color calculator from
//  http://codeitdown.com/hsl-hsb-hsv-color/		 
// Corrected brightness and saturation using calculator from
//  http://www.rapidtables.com/convert/color/rgb-to-hsl.htm
def rgbToHSB(rgb) {
	def r = rgb.r
	def g = rgb.g
	def b = rgb.b
	float hue, saturation, brightness;

	float cmax = (r > g) ? r : g;
	if (b > cmax) cmax = b;
	float cmin = (r < g) ? r : g;
	if (b < cmin) cmin = b;

	float delta = (cmax - cmin)
	brightness = ((cmax + cmin) / 2) / 255
	saturation = 0
	if (delta != 0)	saturation = (delta/255) / (1 - ((2*brightness)-1))    	
		
	if (saturation == 0) hue = 0;
	else hue = 0.60 * ((g - b) / (255 -  cmin)) % 360

//	log.debug("h: $hue s: $saturation b: $brightness")
 
	def hsb = [:]    
	hsb = [h: Math.round(hue * 100) as Integer, s: Math.round(saturation * 100) as Integer, b: Math.round(brightness * 100) as Integer]
	hsb
}
