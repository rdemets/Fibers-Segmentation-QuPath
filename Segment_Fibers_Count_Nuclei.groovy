import qupath.lib.objects.PathAnnotationObject
import qupath.lib.roi.RectangleROI
import qupath.imagej.gui.ImageJMacroRunner

// Functional parameters
DownSamp = 4;       // Downsampling factor for IJ processing
TissueThr = 24;     // Tissue threshold (0 - 255)
SplitThr = 40;      // Fiber splitting threshold (distance map pix)
MinCirc = 0.4;      // Minimum fiber circularity (0 - 1)
MinArea = 10000;    // Minimum fiber area (pix)
MaxArea = 100000;   // Maximum fiber area (pix)
NucThr = 0.175;     // Nucleus detection threshold (0 - 1)

// Instantiate IJ Macro Runner
params = new ImageJMacroRunner(getQuPath()).getParameterList()
params.getParameters().get('downsampleFactor').setValue(DownSamp)
params.getParameters().get('sendROI').setValue(false)
params.getParameters().get('sendOverlay').setValue(false)
params.getParameters().get('doParallel').setValue(false)
params.getParameters().get('clearObjects').setValue(false)
params.getParameters().get('getROI').setValue(true)
params.getParameters().get('getOverlay').setValue(false)
params.getParameters().getOverlayAs.setValue("Annotations")

// Reference BoundingBox Class
def RG = getPathClass('Region')
def FB = getPathClass('Fiber')

// Apply default H&E Image Deconvolution
setImageType('BRIGHTFIELD_H_E');
setColorDeconvolutionStains('{"Name" : "H&E default", "Stain 1" : "Hematoxylin", "Values 1" : "0.65111 0.70119 0.29049", "Stain 2" : "Eosin", "Values 2" : "0.2159 0.8012 0.5581", "Background" : " 255 255 255"}');
    
// Reset Selection    
resetSelection();
    
// Define IJ Macro
def macro = 'rename("Original");setMinAndMax(0,255);run("8-bit");run("Invert");'+
'run("Subtract Background...", "rolling='+Math.round(400/DownSamp)+'");'+
'setThreshold('+Math.round(TissueThr)+', 255, "raw");'+
'setOption("BlackBackground", false);'+
'run("Convert to Mask");'+
'run("Fill Holes");'+
'run("Duplicate...", "title=DistanceMap");'+
'run("Distance Map");'+
'run("Find Maxima...", "prominence='+Math.round(SplitThr/DownSamp)+' light output=[Segmented Particles]");'+
'rename("WatershedLines");'+
'imageCalculator("AND", "Original","WatershedLines");'+
'run("Analyze Particles...", "size='+Math.round(MinArea/DownSamp)+'-'+Math.round(MaxArea/DownSamp)+' pixel circularity='+MinCirc+'-1 show=Masks");rename("CleanedMask");'+
'selectImage("DistanceMap");'+
'close();'+
'selectImage("WatershedLines");'+
'close();'+
'selectImage("Original");'+
'close();'+
'selectImage("CleanedMask");'+
'run("Create Selection");'

// Retrieve image and BoundingBox Annotations
def imageData = getCurrentImageData()

// Delete all non "Regions" Annotations
selectObjects { it -> it.getPathClass() != RG && it.isAnnotation() }
clearSelectedObjects(false)
fireHierarchyUpdate()

// Parse "Regions" Annotations and if none defined set one to whole image
def annotations = getAnnotationObjects().findAll {it.getPathClass() == RG}
if (annotations.size()==0)
{ 
    addObject(PathObjects.createAnnotationObject(new RectangleROI(0,0,imageData.getServer().getWidth(),imageData.getServer().getHeight())))
    annotations = getAnnotationObjects()
    for (annotation in annotations)annotation.setPathClass(RG)
}
print("Number of Regions to process: "+annotations.size())

// Main loop on Regions
if (annotations.size()>0)
{
    // Run the ImageJ macro on each Region
    print("Image analysis in progress...")
    for (annotation in annotations)
    {
        print("Region size: "+annotation.getROI().getBoundsWidth()+" , "+annotation.getROI().getBoundsHeight())
        ImageJMacroRunner.runMacro(params, imageData, null, annotation, macro)
    }
    selectAnnotations();
      
    // Split composite annotations
    // (this can lead to small annotations not removed by IJ since 4-connectivity is used)
    runPlugin('qupath.lib.plugins.objects.SplitAnnotationsPlugin', '{}')
 
    // Select non Region Annotations and set class to Fiber if in valid area range
    selectObjects { it -> it.getPathClass() != RG && it.isAnnotation() }
    def nonRGs = getSelectedObjects()
    for (nonRG in nonRGs)
    {
        if(nonRG.getROI().getArea() >= MinArea)nonRG.setPathClass(FB)
    }
    
    // Select Fibers
    selectObjects { it -> it.getPathClass() == FB && it.isAnnotation() }
    def fibers = getSelectedObjects()
    print("Number of Fibers: "+fibers.size())
   
    // Run nucleus detection on all Fibers
    runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImageBrightfield":"Hematoxylin OD","requestedPixelSizeMicrons":0.5,"backgroundRadiusMicrons":8.0,"backgroundByReconstruction":true,"medianRadiusMicrons":0.0,"sigmaMicrons":1.5,"minAreaMicrons":10.0,"maxAreaMicrons":400.0,"threshold":'+NucThr+',"maxBackground":2.0,"watershedPostProcess":true,"cellExpansionMicrons":2.0,"includeNuclei":true,"smoothBoundaries":true,"makeMeasurements":true}')
    
}

// Update annotations
fireHierarchyUpdate()
//resetSelection();
