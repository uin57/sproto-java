package main

import (
	"bytes"
	"fmt"
	//"github.com/davyxu/gosproto/meta"
	"github.com/davyxu/gosproto/meta"
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"
	"text/template"
)

const javaCodeTemplate = `// Generated by github.com/davyxu/gosproto/sprotogen
// DO NOT EDIT!
{{with .model}}
package {{.JavaPackageName}};
import java.util.*;
import sproto.*;
import java.nio.ByteBuffer;
import static sproto.Util.enums;

public class {{.Name}} implements SprotoObject{
     private Tags tags=new Tags();
    {{range .StFields}}
    private {{.JavaFieldTypeName}} {{.Name}};
    {{end}}
    {{range .StFields}}
    public {{.JavaFieldTypeName}} get{{.UpperName}}(){
        return {{.Name}};
    }
    public void set{{.UpperName}}({{.JavaFieldTypeName}} val){
        this.{{.Name}}=val;
        tags.setTag({{.FieldIndex}});
    }
    public boolean isSet{{.UpperName}}(){
        return tags.hasTag({{.FieldIndex}});
    }
    {{if .IsExtendType}}
    public void set{{.UpperName}}Ext(float val){
    	this.{{.Name}}=({{.JavaFieldTypeName}})(val*(1f/{{.ExtendTypePrecision}}));
    	tags.setTag({{.FieldIndex}});
    }
    public float get{{.UpperName}}Ext(){
        return {{.Name}}*{{.ExtendTypePrecision}};
    }
    {{end}}
    {{end}}
    public  void decode(Decoder decoder){
        int[] tags=decoder.tags();
        for(int i=0;i<tags.length;i++){
            int tag=tags[i];
            {{range .StFields}}
            if(tag=={{.FieldIndex}}){
                this.{{.Name}} = {{.JavaDecodeMethodName}};
            }
            {{end}}
        }
    }
    public  void encode(Encoder encoder){
        tags.each(tag->{
            {{range .StFields}}
            if(tag=={{.FieldIndex}}){ {{.JavaEncodeMethodName}}; }
            {{end}}
        });
    }
}
{{end}}
`

const javaEnumTemplate = `// Generated by github.com/davyxu/gosproto/sprotogen
// DO NOT EDIT!
{{with .model}}
package {{.JavaPackageName}};
import sproto.*;

public enum {{.Name}} {
	{{range .StFields}}
		{{.Name}},
	{{end}};
    public static {{.Name}} byInt(int o){
        {{.Name}}[] vals={{.Name}}.values();
        for (int i = 0; i < vals.length; i++) {
            {{.Name}} val = vals[i];
            if (val.ordinal()==o){
                return val;
            }
        }
        return vals.length>0?vals[0]:null;
    }
}
{{end}}
`

func (self *fieldModel) JavaDecodeMethodName() string {
	var b bytes.Buffer
	// 字段类型映射go的类型
	switch self.Type {
	case meta.FieldType_Integer, meta.FieldType_Int32:
		if self.Repeatd {
			return "decoder.readIntArray()"
		} else {
			return fmt.Sprintf("(int)decoder.readNumber(%d)", self.FieldIndex)
		}
	case meta.FieldType_Bool:
		if self.Repeatd {
			return "decoder.readBooleanArray()"
		} else {
			return fmt.Sprintf("decoder.readBool(%d)", self.FieldIndex)
		}
	case meta.FieldType_Struct:
		if self.Repeatd {
			if self.MainIndex != nil {
				tmpFm := &fieldModel{
					FieldDescriptor: self.MainIndex,
					FieldIndex:      0,
					st:              self.st,
				}

				return fmt.Sprintf("decoder.readMap(new HashMap<>(),%s::new,%s::get%s)", self.GetFullComplexClassName(), self.GetFullComplexClassName(), tmpFm.UpperName())
			}
			return fmt.Sprintf("decoder.readList(new LinkedList<>(),%s::new)", self.GetFullComplexClassName())
		} else {
			return fmt.Sprintf("decoder.readObject(%s::new)", self.GetFullComplexClassName())
		}
	case meta.FieldType_Enum:
		if self.Repeatd {
			return fmt.Sprintf("enums(decoder.readIntArray(),o->%s.values()[o])", self.GetFullComplexClassName())
		} else {
			return fmt.Sprintf("%s.byInt((int)decoder.readNumber(%d))", self.GetFullComplexClassName(), self.FieldIndex)
		}
	case meta.FieldType_Float32, meta.FieldType_Float64, meta.FieldType_UInt32, meta.FieldType_UInt64, meta.FieldType_Int64:
		if self.Repeatd {
			return fmt.Sprintf("decoder.readLongArray()")
		} else {
			return fmt.Sprintf("decoder.readNumber(%d)", self.FieldIndex)
		}
	case meta.FieldType_String:
		if self.Repeatd {
			return "decoder.readStringArray()"
		} else {
			return "decoder.readString()"
		}
	case meta.FieldType_Bytes:
		return "decoder.readBinary()"
	default:
		b.WriteString(self.Type.String())
	}
	return b.String()
}
func (self *fieldModel) JavaEncodeMethodName() string {
	var b bytes.Buffer
	// 字段类型映射go的类型
	switch self.Type {
	case meta.FieldType_Integer, meta.FieldType_Int32:
		if self.Repeatd {
			return fmt.Sprintf("encoder.writeNumberArray(%d,%s)", self.FieldIndex, self.Name)
		} else {
			return fmt.Sprintf("encoder.writeNumber(%d,%s)", self.FieldIndex, self.Name)
		}
	case meta.FieldType_Bool:
		if self.Repeatd {
			return fmt.Sprintf("encoder.writeBoolArray(%d,%s)", self.FieldIndex, self.Name)
		} else {
			return fmt.Sprintf("encoder.writeNumber(%d,%s?1:0)", self.FieldIndex, self.Name)
		}
	case meta.FieldType_Struct:
		if self.Repeatd {
			if self.MainIndex != nil {
				return fmt.Sprintf("encoder.writeObjectList(%d,%s.values())", self.FieldIndex, self.Name)
			}
			return fmt.Sprintf("encoder.writeObjectList(%d,%s)", self.FieldIndex, self.Name)
		} else {
			return fmt.Sprintf("encoder.writeObject(%d,%s)", self.FieldIndex, self.Name)
		}
	case meta.FieldType_Enum:
		if self.Repeatd {
			return fmt.Sprintf("int[] val=enums(%s,%s::ordinal);encoder.writeNumberArray(%d,val)", self.Name, self.GetFullComplexClassName(), self.FieldIndex)
		} else {
			return fmt.Sprintf("encoder.writeNumber(%d,%s.ordinal())", self.FieldIndex, self.Name)
		}
	case meta.FieldType_Float32, meta.FieldType_Float64, meta.FieldType_UInt32, meta.FieldType_UInt64, meta.FieldType_Int64:
		if self.Repeatd {
			return fmt.Sprintf("encoder.writeNumberArray(%d,%s)", self.FieldIndex, self.Name)
		} else {
			return fmt.Sprintf("encoder.writeNumber(%d,%s)", self.FieldIndex, self.Name)
		}
	case meta.FieldType_String:
		if self.Repeatd {
			return fmt.Sprintf("encoder.writeStringArray(%d,%s)", self.FieldIndex, self.Name)
		} else {
			return fmt.Sprintf("encoder.writeString(%d,%s)", self.FieldIndex, self.Name)
		}
	case meta.FieldType_Bytes:
		if self.Repeatd {
			return fmt.Sprintf("encoder.writeBinaryArray(%d,%s)", self.FieldIndex, self.Name)
		} else {
			return fmt.Sprintf("encoder.writeBinary(%d,%s)", self.FieldIndex, self.Name)
		}
	default:
		b.WriteString(self.Type.String())
	}
	return b.String()
}
func (self *fieldModel) JavaFieldTypeName() string {
	var b bytes.Buffer
	var inserArray = false
	// 字段类型映射go的类型
	switch self.Type {
	case meta.FieldType_Integer, meta.FieldType_Int32:
		b.WriteString("int")
	case meta.FieldType_Bool:
		b.WriteString("boolean")
	case meta.FieldType_Struct,
		meta.FieldType_Enum:
		{
			if self.Repeatd {
				if self.MainIndex != nil {
					tmpFm := &fieldModel{
						FieldDescriptor: self.MainIndex,
						FieldIndex:      0,
						st:              self.st,
					}
					b.WriteString("Map<" + tmpFm.JavaFieldTypeName() + ",")
					b.WriteString(self.GetFullComplexClassName() + ">")
				} else {
					b.WriteString("List<" + self.GetFullComplexClassName() + ">")
				}
			} else {
				b.WriteString(self.GetFullComplexClassName())
			}

			inserArray = true
		}
	case meta.FieldType_Float32, meta.FieldType_Float64, meta.FieldType_UInt32, meta.FieldType_UInt64, meta.FieldType_Int64:
		b.WriteString("long")
	case meta.FieldType_Bytes:
		b.WriteString("ByteBuffer")
	case meta.FieldType_String:
		b.WriteString("String")
	default:
		b.WriteString(self.Type.String())
	}
	if self.Repeatd && !inserArray {
		b.WriteString("[]")
	}
	return b.String()
}
func (self *fieldModel) JavaFieldName() string {
	var b bytes.Buffer

	b.WriteString(self.Name)
	return b.String()
}
func (self *fieldModel) JavaFieldAccessorName() string {
	var b bytes.Buffer
	b.WriteString(self.Name)
	return b.String()
}
func (self *structModel) JavaPackageName() string {
	packageName := ""
	prefix := "javaPackage:"
	leadingComment := self.CommentGroup.Leading
	if strings.HasPrefix(leadingComment, prefix) {
		packageName = strings.TrimPrefix(leadingComment, prefix)
	}
	if packageStr, ok := self.CommentGroup.MatchTag("javaPackage"); ok {
		packageName = strings.TrimSpace(packageStr)
	}
	if packageName == "" {
		return self.f.PackageName
	}
	return strings.Join([]string{self.f.PackageName, packageName}, ".")
}
func (self *fieldModel) GetFullComplexClassName() string {
	st := self.st
	fm := self.st.f
	for _, k := range fm.Objects {
		if self.Complex.Name == k.Name {
			if st.JavaPackageName() == k.JavaPackageName() {
				return k.Name
			} else {
				return k.JavaPackageName() + "." + k.Name
			}
		}
	}
	return self.Complex.Name
}

func gen_java(fm *fileModel, filename string) {
	addData(fm, "java")
	for _, k := range fm.Structs {
		fileName := filename + "/" + strings.Replace(k.JavaPackageName(), ".", "/", -1) + "/" + k.Name + ".java"

		generateJavaCode("sp.struct->java", javaCodeTemplate, fileName, map[string]interface{}{
			"model": k,
			"fm":    fm,
		})
	}
	for _, k := range fm.Enums {
		fileName := filename + "/" + strings.Replace(k.JavaPackageName(), ".", "/", -1) + "/" + k.Name + ".java"
		generateJavaCode("sp.enum->java", javaEnumTemplate, fileName, map[string]interface{}{
			"model": k,
			"fm":    fm,
		})
	}
}

func generateJavaCode(templateName, templateStr, fileName string, model interface{}) {
	var err error
	var bf bytes.Buffer

	tpl, err := template.New(templateName).Parse(templateStr)
	if err != nil {
		goto OnError
	}

	err = tpl.Execute(&bf, model)
	if err != nil {
		goto OnError
	}

	if fileName != "" {

		os.MkdirAll(filepath.Dir(fileName), 666)

		err = ioutil.WriteFile(fileName, bf.Bytes(), 0666)

		if err != nil {
			goto OnError
		}
	}
	return

OnError:
	fmt.Println(err)
	os.Exit(1)
}
