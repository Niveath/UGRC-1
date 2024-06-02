# UGRC-1

# Compile-Time Memory Management

Traditional garbage collectors used by languages such as Java employ a reference-based method to identify when objects can be safely killed and their memory reclaimed. This causes severe sub-optimal usage of memory as several objects that may be still referenced but no longer used are not collected by the garbage collector. Prior works such as [1] and [2] show that it is possible to such objects. While [1] is intra-procedural and [2] is complex, we present a easy to implement solution for this problem. We build a inter-procedural liveness analysis framework on top of a flow-insensitive and context-insensitive points-to analysis to identify dead objects. The objects are then freed by setting appropriate references to null explicitly. We implement our solution for Java using the Soot Compiler Optimization Framework and present its working for a few small programs.

The code can be run on Eclipse by setting up the latest Soot and an approriate Java 8 JRE.

Refer to our README file on running the docker image of the project. Find our docker image [here](https://drive.google.com/drive/u/0/folders/1FTbOu1n7D9wjnH09BDyQw5G0hySfthQh).

Find our report [here](https://drive.google.com/file/d/1Sq27zZ4AeQagUSnfBF0KMyOqrg6_xSvq/view?usp=drive_link)

Find our presentation [here](https://drive.google.com/file/d/1okC6OMrErEPtEy5JJqGzGOMtuwyJi2u9/view?usp=sharing)
