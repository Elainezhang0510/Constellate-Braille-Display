"""Parametric CadQuery source. All dimensions mm. pip install cadquery trimesh matplotlib"""
from pathlib import Path
import sys, json, math
import os
workspace=Path(__file__).resolve().parent
os.environ.setdefault('MPLCONFIGDIR',str(workspace/'work'/'mpl-cache'))
local=workspace/'work'/'cadlibs'
if local.exists(): sys.path.insert(0,str(local))
import cadquery as cq
import trimesh
OUT=Path(__file__).resolve().parent
W,L=40.,56.
WALL,FLOOR=2.4,2.4
SEAT=13.
BODY_D,BODY_H=10.,19.5
BORE,HEAD_HOLE=10.5,4.6
BASE_H,LID_T=34.6,2.4
GUIDE_MOUTH=8.0
WINDOW_START,WINDOW_LENGTH=8.5,39.0
VENT_LENGTH,VENT_HEIGHT=20.0,1.2
VENT_START=(L-VENT_LENGTH)/2
VENT_Z=30.0-VENT_HEIGHT/2
CENTERS=[(x,y) for y in (12.,28.,44.) for x in (12.,28.)]
SCREWS=[(x,y) for y in (4.8,51.2) for x in (4.8,35.2)]
def box(x,y,z,dx,dy,dz):
    return cq.Workplane('XY').box(dx,dy,dz,centered=(False,False,False)).translate((x,y,z))
def cyl(x,y,z,d,h):
    return cq.Workplane('XY').circle(d/2).extrude(h).translate((x,y,z))
base=box(0,0,0,W,L,BASE_H).cut(box(WALL,WALL,FLOOR,W-2*WALL,L-2*WALL,BASE_H))
for x,y in CENTERS:
    # Perforated pedestal leaves tail travel clear; narrow windows carry wire below seat.
    pedestal=cyl(x,y,FLOOR,13.8,SEAT-FLOOR).cut(cyl(x,y,FLOOR-.1,8.,SEAT-FLOOR+.2))
    pedestal=pedestal.cut(box(x-8,y-3,3.,16.,6.,6.2))
    shoulder=cyl(x,y,10.8,13.8,2.2).cut(cyl(x,y,10.7,4.8,2.4))
    guide=cyl(x,y,SEAT,13.8,14.).cut(cyl(x,y,SEAT-.1,BORE,14.2))
    # Each column opens toward its nearest long side. Keep the seat and tail clearance intact.
    mouth_x=x-8. if x<W/2 else x
    guide=guide.cut(box(mouth_x,y-GUIDE_MOUTH/2,SEAT-.1,8.,GUIDE_MOUTH,14.2))
    base=base.union(pedestal).union(shoulder).union(guide)
for x,y in SCREWS:
    base=base.union(cyl(x,y,FLOOR,6.4,BASE_H-FLOOR))
    base=base.cut(cyl(x,y,BASE_H-9.,2.1,10.))
# Keep lower 39 mm windows. Upper windows become centered 20 x 1.2 mm vents.
# End regions stay 8.5 mm wide, clear of the 6.4 mm screw bosses.
base=base.cut(box(16.,-.1,5.,8.,WALL+.2,5.))
for x in (-.1,W-WALL-.1):
    base=base.cut(box(x,WINDOW_START,5.,WALL+.2,WINDOW_LENGTH,4.))
    base=base.cut(box(x,VENT_START,VENT_Z,WALL+.2,VENT_LENGTH,VENT_HEIGHT))
base=base.clean()
lid=box(0,0,BASE_H,W,L,LID_T)
# Small underside pads axially retain the metal cans. Recesses clear black necks.
for x,y in CENTERS:
    lid=lid.union(cyl(x,y,32.8,13.8,1.8))
    lid=lid.cut(cyl(x,y,32.7,8.6,2.8))
    lid=lid.cut(cyl(x,y,32.7,HEAD_HOLE,4.5))
for x,y in SCREWS:
    lid=lid.cut(cyl(x,y,34.5,2.9,3.))
lid=lid.clean()
# Print lid outer face on bed: all neck pockets face up, no supports.
lid_print=lid.rotate((0,0,0),(1,0,0),180).translate((0,L,BASE_H+LID_T))
coupon=box(0,0,0,30,16,2.4)
coupon=coupon.union(cyl(8,8,2.4,13.8,8)).cut(cyl(8,8,-.1,BORE,10.7))
coupon=coupon.union(cyl(23,8,2.4,10,1.5)).cut(cyl(23,8,-.1,HEAD_HOLE,4.2)).clean()
parts={'base':base,'lid_print':lid_print,'fit_coupon':coupon}
report={'revision':'v3','units':'mm','outer_mm':[W,L,BASE_H+LID_T],'hole_centers_mm':CENTERS,'parts':{},'revision_dimensions':{'guide_mouth_width_mm':GUIDE_MOUTH,'mouth_direction':'outward toward x=0 / x=40 long sides','lower_window_length_mm':WINDOW_LENGTH,'upper_vent_length_mm':VENT_LENGTH,'upper_vent_height_mm':VENT_HEIGHT,'upper_vent_y_mm':[VENT_START,VENT_START+VENT_LENGTH],'window_end_regions_mm':WINDOW_START,'lower_window_z_mm':[5,9],'upper_window_z_mm':[VENT_Z,VENT_Z+VENT_HEIGHT],'top_rail_height_mm':BASE_H-VENT_Z-VENT_HEIGHT}}
for name,part in parts.items():
    shape=part.val()
    assert shape.isValid() and len(shape.Solids())==1,(name,'invalid/disconnected')
    cq.exporters.export(part,str(OUT/(name+'.step')))
    cq.exporters.export(part,str(OUT/(name+'.stl')),tolerance=.04,angularTolerance=.12)
    mesh=trimesh.load_mesh(OUT/(name+'.stl'))
    assert mesh.is_watertight and mesh.is_winding_consistent and mesh.volume>0,name
    bb=shape.BoundingBox()
    report['parts'][name]={'valid_solid':True,'watertight_stl':True,'volume_mm3':shape.Volume(),'bounds_mm':[bb.xlen,bb.ylen,bb.zlen],'solid_count':len(shape.Solids())}
assert base.intersect(lid).val().Volume()<1e-5,'lid collision'
reference=[]
for x,y in CENTERS:
    body=cyl(x,y,SEAT,BODY_D,BODY_H)
    neck=cyl(x,y,SEAT+BODY_H,7.5,2.9) # ASSUMED neck envelope, not supplied by vendor
    shaft=cyl(x,y,SEAT+BODY_H+2.9,2.2,6.5)
    tail=cyl(x,y,SEAT-4,2.2,4)
    actuator=body.union(neck).union(shaft).union(tail)
    assert base.intersect(actuator).val().Volume()<1e-5,'actuator/base interference'
    assert lid.intersect(actuator).val().Volume()<1e-5,'actuator/lid interference'
    # Conservative 4 mm diameter lower tail swept 5.5 mm in either direction.
    sweep=cyl(x,y,SEAT-9.5,4.,9.5)
    assert base.intersect(sweep).val().Volume()<1e-5,'lower stroke interference'
    reference.append(actuator)
assembly=cq.Assembly(name='container')
assembly.add(base,name='base',color=cq.Color(.20,.35,.42))
assembly.add(lid,name='lid',color=cq.Color(.75,.79,.78))
assembly.export(str(OUT/'container_assembly.step'))
ref=cq.Assembly(name='reference_only_DO_NOT_PRINT')
ref.add(base,name='base');ref.add(lid,name='lid')
for i,a in enumerate(reference):ref.add(a,name=f'actuator_envelope_{i+1}')
ref.export(str(OUT/'assembly_with_reference.step'))
report['collision_checks']={'base_vs_lid':True,'six_nominal_envelopes':True,'lower_5_5mm_swept_tail':True}
report['nominal_head_exposure_mm']=4.9
report['print_material']='PETG'
report['solid_petg_mass_g']=sum(report['parts'][n]['volume_mm3'] for n in ('base','lid_print'))/1000*1.27
# Optional revision comparison when the prior release is alongside this folder.
previous=OUT.parent/'v2'
if (previous/'lid_print.step').exists() and (previous/'base.step').exists():
    old_lid=cq.importers.importStep(str(previous/'lid_print.step'))
    report['lid_unchanged']=old_lid.cut(lid_print).val().Volume()<1e-5 and lid_print.cut(old_lid).val().Volume()<1e-5
    assert report['lid_unchanged'],'lid changed'
    old_base=cq.importers.importStep(str(previous/'base.step'))
    allowed=[]
    for x in (-.1,W-WALL-.1):
        for z,h in ((29.,2.),):
            allowed.append(box(x,WINDOW_START-.1,z-.1,WALL+.2,WINDOW_LENGTH+.2,h+.2))
    changed=old_base.cut(base).union(base.cut(old_base))
    for region in allowed:changed=changed.cut(region)
    report['unchanged_outside_requested_regions']=changed.val().Volume()<1e-5
    assert report['unchanged_outside_requested_regions'],'unintended change'
else:
    report['prior_revision_comparison']='skipped: previous release not present'
for x,y in CENTERS:
    side=-1 if x<W/2 else 1
    mouth_probe=box(x-7 if side<0 else x,y-3.9,SEAT+.1,7.,7.8,13.8)
    assert base.intersect(mouth_probe).val().Volume()<1e-5,'blocked mouth'
    back_probe=cyl(x-side*6.1,y,SEAT+1.,.5,12.)
    assert base.intersect(back_probe).val().Volume()>.99*back_probe.val().Volume(),'missing sleeve back'
report['six_outward_mouths_verified']=True
(OUT/'validation.json').write_text(json.dumps(report,indent=2),encoding='utf8')
# Static technical preview, actual triangulated CAD geometry.
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
fig=plt.figure(figsize=(12,7),facecolor='#f5f6f6')
for k,(title,mode) in enumerate([('Assembled | 40 x 56 x 37 mm','closed'),('Exploded | 2 columns x 3 rows','open')]):
    ax=fig.add_subplot(1,2,k+1,projection='3d')
    scene_triangles=[]
    scene_colors=[]
    def draw(part,color,alpha=1):
        vertices,faces=part.val().tessellate(.18,.2)
        v=[p.toTuple() for p in vertices]
        triangles=[[v[i] for i in f] for f in faces]
        scene_triangles.extend(triangles)
        scene_colors.extend([color]*len(triangles))
    draw(base,'#4d707f')
    if mode=='open':
        for a in reference:draw(a,'#a7a9ac')
        draw(lid.translate((0,0,17)),'#c6ccce')
    else:
        draw(lid,'#c6ccce')
        for a in reference:draw(a,'#a7a9ac')
    ax.add_collection3d(Poly3DCollection(scene_triangles,facecolors=scene_colors,linewidths=0,shade=True))
    ax.set_xlim(0,W);ax.set_ylim(0,L);ax.set_zlim(0,60 if mode=='open' else 43)
    ax.set_box_aspect((W,L,60 if mode=='open' else 43));ax.view_init(elev=28,azim=-58)
    ax.set_xlabel('X (mm)');ax.set_ylabel('Y (mm)');ax.set_zlabel('Z (mm)')
    ax.set_title(title,fontsize=12,pad=15);ax.set_facecolor('#f5f6f6')
fig.suptitle('Six-actuator enclosure | external Raspberry Pi | PETG',fontsize=16)
fig.text(.05,.03,'CAD-derived geometry. Actuator neck diameter is an assumed envelope; confirm on hardware.',fontsize=10)
fig.subplots_adjust(left=.02,right=.98,bottom=.10,top=.88,wspace=.05)
fig.savefig(OUT/'preview.png',dpi=160)
print(json.dumps(report,indent=2))
