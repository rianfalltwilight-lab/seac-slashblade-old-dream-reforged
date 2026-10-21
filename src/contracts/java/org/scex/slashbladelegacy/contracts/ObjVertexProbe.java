package org.scex.slashbladelegacy.contracts;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Compares actual mesh attributes after the measured render windows. */
public final class ObjVertexProbe {
    private static final int COLOR=0x8A35C7E2;
    private static final int LIGHT=0x00D00070;

    private record Fixture(String pose,String normals,String uv,boolean alphaYzz) {
        String name(){return pose+"/"+normals+"/"+uv+"/"+(alphaYzz?"alpha_yzz":"alpha_default");}
    }
    private record Mesh(byte[] attributes,int vertices,int stride,int attributeSize,
                        int matrixCalls,int bulkCalls,int faceCalls,int faceNormalCalls) {}

    public static void run(Path output)throws Exception{
        var rows=new ArrayList<Map<String,Object>>();
        var previousUv=Face.uvOperator;
        var previousAlpha=Face.alphaOverride;
        try{
            for(var format:List.of(DefaultVertexFormat.NEW_ENTITY,DefaultVertexFormat.POSITION_TEX)){
                for(String pose:List.of("identity","uniform","nonuniform"))
                    for(String normals:List.of("vertex","face","calculated"))
                        for(String uv:List.of("default","scaled","missing","unsigned"))
                            for(boolean alpha:new boolean[]{false,true}){
                                var fixture=new Fixture(pose,normals,uv,alpha);
                                record(rows,formatName(format)+"/"+fixture.name(),()->{
                                    var expected=render(fixture,format,1,false);
                                    var actual=render(fixture,format,0,false);
                                    return equivalent(expected,actual);
                                });
                            }
                var customFixture=new Fixture("nonuniform","calculated","scaled",true);
                record(rows,formatName(format)+"/custom_consumer",()->{
                    var actual=render(customFixture,format,2,false);
                    check(actual.matrixCalls()==actual.vertices(),"Custom consumer matrix overload was bypassed");
                    check(actual.bulkCalls()==0,"Custom consumer received the bulk fast path");
                    var ordinary=render(customFixture,format,1,false);
                    check(!Arrays.equals(actual.attributes(),ordinary.attributes()),"Custom consumer position change was lost");
                    return Map.of("vertices",actual.vertices(),"matrix_calls",actual.matrixCalls(),"bulk_calls",actual.bulkCalls());
                });
                record(rows,formatName(format)+"/custom_face",()->{
                    var expected=render(customFixture,format,1,true);
                    var actual=render(customFixture,format,0,true);
                    check(actual.faceCalls()==1 && actual.faceNormalCalls()==1,"Custom Face overrides were bypassed");
                    var details=new LinkedHashMap<String,Object>(equivalent(expected,actual));
                    details.put("face_calls",actual.faceCalls());details.put("face_normal_calls",actual.faceNormalCalls());
                    return details;
                });
            }
        }finally{
            Face.uvOperator=previousUv;Face.alphaOverride=previousAlpha;
        }
        boolean passed=rows.stream().allMatch(row->Boolean.TRUE.equals(row.get("passed")));
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "passed",passed,"case_count",rows.size(),"cases",rows,
                "comparison","Exact bytes for every declared vertex attribute. Unwritten format padding is excluded.",
                "reference","ReferenceBufferBuilder inherits all methods; its distinct class preserves the original Face path.",
                "scope","Synthetic OBJ faces through real BufferBuilder and MeshData; runs after render metrics and JFR are saved.")));
        check(passed,"OBJ vertex equivalence failed; see "+output);
    }

    private static String formatName(VertexFormat format){return format==DefaultVertexFormat.NEW_ENTITY?"NEW_ENTITY":"POSITION_TEX";}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    @FunctionalInterface private interface Case { Map<String,Object> run()throws Exception; }
    private static void record(List<Map<String,Object>> rows,String name,Case test){
        var row=new LinkedHashMap<String,Object>();row.put("case",name);
        try{row.putAll(test.run());row.put("passed",true);}
        catch(Exception e){row.put("passed",false);row.put("error",e.toString());}
        rows.add(row);
    }

    private static Map<String,Object> equivalent(Mesh expected,Mesh actual){
        check(expected.vertices()==actual.vertices(),"Vertex count changed");
        check(expected.stride()==actual.stride(),"Vertex stride changed");
        check(expected.attributes().length==actual.attributes().length,"Attribute byte count changed");
        int mismatch=Arrays.mismatch(expected.attributes(),actual.attributes());
        check(mismatch<0,"Attribute byte mismatch at "+mismatch+"; expected="
                +(mismatch<0?"none":Byte.toUnsignedInt(expected.attributes()[mismatch]))+", actual="
                +(mismatch<0?"none":Byte.toUnsignedInt(actual.attributes()[mismatch])));
        return Map.of("vertices",actual.vertices(),"compared_bytes",actual.attributes().length,
                "stride",actual.stride(),"padding_bytes_per_vertex",actual.stride()-actual.attributeSize());
    }

    private static Mesh render(Fixture fixture,VertexFormat format,int consumerKind,boolean customFace){
        Face face=customFace?new CustomFace():new Face();
        face.vertices=new Vertex[]{new Vertex(-1.25F,0,.375F),new Vertex(1.125F,.75F,-.625F),new Vertex(.25F,-1.5F,.5F)};
        if(fixture.normals().equals("vertex"))face.vertexNormals=new Vertex[]{new Vertex(.25F,2,-.75F),new Vertex(-1,.5F,.3F),new Vertex(.2F,.4F,3)};
        if(fixture.normals().equals("face"))face.faceNormal=new Vertex(.3F,1.75F,-.6F);
        if(!fixture.uv().equals("missing")){
            face.textureCoordinates=new TextureCoordinate[]{new TextureCoordinate(.125F,.25F),new TextureCoordinate(.875F,.375F),new TextureCoordinate(.375F,.9375F)};
            if(!fixture.uv().equals("unsigned"))face.initTexSigns();
        }
        if(fixture.uv().equals("scaled"))Face.setUvOperator(-1.25F,.75F,-.2F,.4F);
        else Face.resetUvOperator();
        Face.setAlphaOverride(fixture.alphaYzz()?Face.alphaOverrideYZZ:Face.alphaNoOverride);
        var poses=new PoseStack();
        if(!fixture.pose().equals("identity")){
            poses.translate(.625,-1.125,2.25);
            poses.mulPose(new Quaternionf().rotationXYZ(.37F,-.81F,.23F));
            if(fixture.pose().equals("uniform"))poses.scale(-.008F,.008F,.008F);
            else poses.scale(-1.75F,.625F,2.25F);
        }
        try(var storage=new ByteBufferBuilder(1024)){
            BufferBuilder buffer=switch(consumerKind){
                case 1->new ReferenceBufferBuilder(storage,format);
                case 2->new CustomBufferBuilder(storage,format);
                default->new BufferBuilder(storage,VertexFormat.Mode.TRIANGLES,format);
            };
            face.addFaceForRender(buffer,.0005F,poses.last().pose(),poses.last().normal(),LIGHT,COLOR);
            try(var mesh=buffer.buildOrThrow()){
                check(mesh.drawState().vertexCount()==3,"Fixture did not produce one triangle");
                int attributeSize=format.getElements().stream().mapToInt(element->element.byteSize()).sum();
                byte[] bytes=attributes(mesh.vertexBuffer(),format,mesh.drawState().vertexCount());
                var customBuffer=buffer instanceof CustomBufferBuilder value?value:null;
                var custom=face instanceof CustomFace value?value:null;
                return new Mesh(bytes,mesh.drawState().vertexCount(),format.getVertexSize(),attributeSize,
                        customBuffer==null?0:customBuffer.matrixCalls,customBuffer==null?0:customBuffer.bulkCalls,
                        custom==null?0:custom.calls,custom==null?0:custom.normalCalls);
            }
        }
    }

    private static byte[] attributes(ByteBuffer vertices,VertexFormat format,int count){
        var bytes=new ByteArrayOutputStream(count*format.getVertexSize());
        for(int vertex=0;vertex<count;vertex++)
            for(var element:format.getElements()){
                int offset=vertex*format.getVertexSize()+format.getOffset(element);
                for(int i=0;i<element.byteSize();i++)bytes.write(vertices.get(offset+i));
            }
        return bytes.toByteArray();
    }

    /** A different class selects the original path without changing its methods. */
    private static class ReferenceBufferBuilder extends BufferBuilder {
        ReferenceBufferBuilder(ByteBufferBuilder storage,VertexFormat format){super(storage,VertexFormat.Mode.TRIANGLES,format);}
    }
    private static final class CustomBufferBuilder extends ReferenceBufferBuilder {
        int matrixCalls,bulkCalls;
        CustomBufferBuilder(ByteBufferBuilder storage,VertexFormat format){super(storage,format);}
        @Override public VertexConsumer addVertex(Matrix4f transform,float x,float y,float z){
            matrixCalls++;return super.addVertex(transform,x+.25F,y-.5F,z+.75F);
        }
        @Override public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz){
            bulkCalls++;super.addVertex(x,y,z,color,u,v,overlay,light,nx,ny,nz);
        }
    }
    private static final class CustomFace extends Face {
        int calls,normalCalls;
        @Override public void addFaceForRender(VertexConsumer consumer,float offset,Matrix4f pose,Matrix3f normal,int light,int color){
            calls++;super.addFaceForRender(consumer,offset,pose,normal,light,color^0x00FFFFFF);
        }
        @Override public Vertex calculateFaceNormal(){normalCalls++;return new Vertex(.3F,.8F,.5F);}
    }
}
