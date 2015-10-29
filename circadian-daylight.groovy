/**
 *  Circadian Daylight
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
 *		2700K (warm white) until your hub goes into Sleep mode
 *
 *  Version 1.1: April 1, 2015 - Add support for contact sensors
 *  Version 1.0: March 30, 2015 - Initial release
 *  
 *  The latest version of this file can be found at
 *     https://github.com/KristopherKubicki/smartapp-circadian-daylight/blob/master/circadian-daylight.groovy
 *   
 */

definition(
	name: "Circadian Daylight",
	namespace: "KristopherKubicki",
	author: "kristopher@acm.org",
	description: "Sync your color changing lights with natural daylight hues",
	category: "Green Living",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/hue.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/hue@2x.png"
)

preferences {
	section("When these switches turn on/off...") {
		input "switches", "capability.switch", title: "Which switches?", multiple:true
	}
	section("Or these motion sensors are activated...") {
		input "motions", "capability.motionSensor", title: "Which motions?", multiple:true
	}
	section("Or these contact sensors open/close...") {
	 	input "contacts", "capability.contactSensor", title: "Which contacts?", multiple:true
	}
	section("Control these bulbs...") {
		input "bulbs", "capability.colorControl", title: "Which Hue Bulbs?", multiple:true
	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	unschedule()
	initialize()
}


private def initialize() {
	log.debug("initialize() with settings: ${settings}")
	subscribe(switches, "switch", sunHandler)
	subscribe(motions, "motion", sunHandler)
	subscribe(contacts, "contact", sunHandler)
//	schedule("0 */6 * * * ?", sunHandler)			// "gentle" polling every 6 minutes
	atomicState.oldValue = []						// atomic so we avoid calculations on multiple concurrent events
    atomicState.oldTemp = 0
    atomicState.oldBright = 999
}

def sunHandler(evt) {
//	log.debug "$evt.name: $evt.value"
	Integer maxK = 6000
	Integer minK = 2700
	Integer deltaK = maxK-minK
	def brightness = 1

	def after = getSunriseAndSunset()
	def midDay = after.sunrise.time + ((after.sunset.time - after.sunrise.time) / 2)

	def currentTime = now()
//	log.debug "difference is $midDay : mode ${location.mode} :: $currentTime : ${location.mode}"

    Integer temp
    
	if(location.mode == "Night") { 
//		log.info("this is starlight")
		temp = minK
	}
	else if(currentTime < after.sunrise.time) {
//		log.info("this is early twilight")
		temp = minK
	}
	else if(currentTime > after.sunset.time) { 
//		log.info("this is late twilight")
		temp = minK
	}
	else {
		if(currentTime < midDay) { 
			temp = Math.round(minK + ((currentTime - after.sunrise.time) / (midDay - after.sunrise.time) * deltaK)) // we dont need the decimal precision here
			brightness = Math.round(((currentTime - after.sunrise.time) / (midDay - after.sunrise.time)) * 100)
//			log.info("this is morning daylight: $temp $brightness")
		}
		else { 
			temp = Math.round(maxK - ((currentTime - midDay) / (after.sunset.time - midDay) * deltaK)) * 100
			brightness = Math.round(1 - ((currentTime - midDay) / (after.sunset.time - midDay)))
//			log.info("this is afternoon daylight: $temp $brightness")
		}
	}

	if ((temp == atomicState.oldTemp) && (brightness == atomicState.oldBright)) return;			// avoid the redundant calculations for efficiency
    atomicState.oldTemp = temp
    atomicState.oldBright = brightness
    
    def hsb = rgbToHSB(ctToRGB(temp))

	if (location.mode == "Night") { brightness = 2 }			// special for night sky simulation (could/should be bluer value perhaps
    hsb.b = brightness
    
//    else hsb.b = Math.min( Math.round( ( ( temp - ( minK*0.8 ) ) / deltaK ) * 100 ) as Integer, 100);	// use a relative percentage of lightness (20%-->100%)
    
 	def newValue = [hue: Math.round(hsb.h) as Integer, saturation: Math.round(hsb.s) as Integer, level: Math.round(hsb.b) as Integer ?: 1]
	if (newValue != atomicState.oldValue) {				// avoid updating all the lights unnecessarily for efficiency
		atomicState.oldValue = newValue
		log.info "Updated with daylight hueColor: $newValue"
	//	bulbs?.setColor(newValue)
	
		for(bulb in bulbs) {
    		if(bulb.currentValue("switch") == "on") {
				bulb.setColor(newValue)
        	}
		}
	}   
}

// Based on color temperature converter from 
//  http://www.tannerhelland.com/4435/convert-temperature-rgb-algorithm-code/
// As commented, this will not work for color temperatures below 1900 or above 6600
// Remove comments between *uncomment* lines
def ctToRGB(ct) { 
	float r
	float g
	float b
    
//	if (ct < 2500) ct = 2500				// Philips Hue effective minimum (stretched)
//	if (ct > 6500) ct = 6500				// Philips Hue effective maximum (stretched)

// *uncomment below for full algorithm*
//	if (ct < 1000) ct = 1000
//	if (ct > 40000) ct = 40000
	
	ct = ct / 100
//	if (ct <= 66 ) {
		r = 255
		g = 99.4708025861 * Math.log(ct) - 161.1195681661
//	}
//	else {
//		r = 329.698727446 * Math.pow((ct-60), -0.1332047592)
//		g = 288.1221695283 * Math.pow((ct-60), -0.0755148492)
//	}
//	if (r > 255) r = 255
	if (g > 255) g = 255
//	if (ct >= 66) {
//		b = 255
//	}
//	else if (ct <= 19) {
//		b = 0
//	}
//	else {
		b = 138.5177312231 * Math.log(ct - 10) - 305.0447927307
//	}
	if (b > 255) b = 255
// *uncomment above for full algorithm*

//	log.debug("raw-> r: $r g: $g b: $b")

// Apply Hue gamma adjustment for more vibrant & accurate colors
	float red = r/255
	float green = g/255
   	float blue = b/255    
	red = ((red > 0.04045f) ? Math.pow((red + 0.055f) / (1.0f + 0.055f), 2.4f) : (red / 12.92f)) * 255
	green = ((green > 0.04045f) ? Math.pow((green + 0.055f) / (1.0f + 0.055f), 2.4f) : (green / 12.92f)) * 255
	blue = ((blue > 0.04045f) ? Math.pow((blue + 0.055f) / (1.0f + 0.055f), 2.4f) : (blue / 12.92f)) * 255

//	log.debug("gamma-> r: $red g: $green b: $blue")

	def rgb = [:]
	rgb = [r: red, g: green, b: blue]
	rgb
}

// Based on color calculator from
//  http://codeitdown.com/hsl-hsb-hsv-color/
// 
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

	brightness = cmax / 255;
    
	if (cmax != 0) saturation = (cmax - cmin) / cmax;
	else saturation = 0;

	if (saturation == 0) hue = 0;
	else hue = 0.60 * ((g - b) / (255 -  cmin)) % 360

//	log.debug("h: $hue s: $saturation b: $brightness")
 
	def hsb = [:]    
	hsb = [h: hue *100, s: saturation *100, b: brightness * 100]
	hsb
}

// Adapted above to return HSL level and saturation using calculator from
//  http://www.rapidtables.com/convert/color/rgb-to-hsl.htm
//
//def rgbToHSL(rgb) {
//	def r = rgb.r
//	def g = rgb.g
//	def b = rgb.b
//	float hue, saturation, brightness;
//
//	float cmax = (r > g) ? r : g;
//	if (b > cmax) cmax = b;
//	float cmin = (r < g) ? r : g;
//	if (b < cmin) cmin = b;
//	float delta = (cmax - cmin)
//
//	brightness = ((cmax + cmin) / 2) / 255
//	saturation = 0
//	if (delta != 0)	saturation = (delta/255) / (1 - Math.abs((2*brightness)-1) )    	
//
//	if (saturation == 0) hue = 0;
//	else hue = 0.60 * ((g - b) / (255 -  cmin)) % 360
//
//	log.debug("h: $hue s: $saturation b: $brightness")
// 
//	def hsb = [:]    
//	hsb = [h: hue *100, s: saturation *100, b: brightness * 100]
//	hsb
//}
