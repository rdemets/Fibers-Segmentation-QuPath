DownSamp = 4;
TissueThr = 25;
SplitThr = 10;
MinCirc = 0;
MinArea = 1000;
MaxArea = 25000;


rename("Original");
setMinAndMax(0,255);
run("8-bit");
run("Invert");
run("Subtract Background...", "rolling="+d2s(400/DownSamp,0));
setThreshold(TissueThr, 255, "raw");
setOption("BlackBackground", false);
run("Convert to Mask");'
run("Fill Holes");
run("Duplicate...", "title=DistanceMap");
run("Distance Map");
run("Find Maxima...", "prominence="+SplitThr+" light output=[Segmented Particles]");
rename("WatershedLines");
imageCalculator("AND", "Original","WatershedLines");
selectImage("Original");
//waitForUser("Stop");
run("Analyze Particles...", "size="+d2s(MinArea,0)+"-"+d2s(MaxArea,0)+" pixel circularity="+MinCirc+"-1 show=Masks");
rename("CleanedMask");
selectImage("DistanceMap");
close();
selectImage("WatershedLines");
close();
selectImage("Original");
close();
selectImage("CleanedMask");
run("Create Selection");
