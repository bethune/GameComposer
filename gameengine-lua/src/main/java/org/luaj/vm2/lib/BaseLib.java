/*******************************************************************************
* Copyright (c) 2009 Luaj.org. All rights reserved.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
* THE SOFTWARE.
******************************************************************************/
package org.luaj.vm2.lib;

import org.luaj.vm2.Globals;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** 
 * Subclass of {@link LibFunction} which implements the lua basic library functions. 
 * <p>
 * This contains all library functions listed as "basic functions" in the lua documentation for JME. 
 * The functions dofile and loadfile use the 
 * {@link #finder} instance to find resource files.
 * Since JME has no file system by default, {@link BaseLib} implements 
 * {@link ResourceFinder} using {@link Class#getResource(String)}, 
 * which is the closest equivalent on JME.     
 * The default loader chain in {@link PackageLib} will use these as well.
 * <p>  
 * To use basic library functions that include a {@link ResourceFinder} based on 
 * directory lookup, use {@link JseBaseLib} instead. 
 * <p>
 * Typically, this library is included as part of a call to either 
 * {@link JsePlatform#standardGlobals()} or
 * {@link JmePlatform#standardGlobals()}
 * <pre> {@code
 * Globals globals = JsePlatform.standardGlobals();
 * globals.get("print").call(LuaValue.valueOf("hello, world"));
 * } </pre>
 * <p>
 * For special cases where the smallest possible footprint is desired, 
 * a minimal set of libraries could be loaded
 * directly via {@link Globals#load(LuaValue)} using code such as:
 * <pre> {@code
 * Globals globals = new Globals();
 * globals.load(new JseBaseLib());
 * globals.get("print").call(LuaValue.valueOf("hello, world"));
 * } </pre>
 * Doing so will ensure the library is properly initialized 
 * and loaded into the globals table. 
 * <p>
 * This is a direct port of the corresponding library in C.
 * @see JseBaseLib
 * @see ResourceFinder
 * @see #finder
 * @see LibFunction
 * @see JsePlatform
 * @see JmePlatform
 * @see <a href="http://www.lua.org/manual/5.2/manual.html#6.1">Lua 5.2 Base Lib Reference</a>
 */
public class BaseLib extends TwoArgFunction {
	
	Globals globals;
	
	@Override
    public LuaValue call(LuaValue modname, LuaValue env) {
		globals = env.checkglobals();
		globals.baselib = this;
		env.set( "_G", env );
		env.set( "_VERSION", Lua._VERSION );
		env.set("assert", new _assert());
		env.set("error", new error());
		env.set("getmetatable", new getmetatable());
		env.set("pcall", new pcall());
		env.set("print", new print(this));
		env.set("rawequal", new rawequal());
		env.set("rawget", new rawget());
		env.set("rawlen", new rawlen());
		env.set("rawset", new rawset());
		env.set("select", new select());
		env.set("setmetatable", new setmetatable());
		env.set("tonumber", new tonumber());
		env.set("tostring", new tostring());
		env.set("type", new type());
		env.set("xpcall", new xpcall());

		next next;
		env.set("next", next = new next());
		env.set("pairs", new pairs(next));
		env.set("ipairs", new ipairs());
		
		return env;
	}

	// "assert", // ( v [,message] ) -> v, message | ERR
	static final class _assert extends VarArgFunction {
		@Override
        public Varargs invoke(Varargs args) {
			if ( !args.arg1().toboolean() ) 
				error( args.narg()>1? args.optjstring(2,"assertion failed!"): "assertion failed!" );
			return args;
		}
	}

	// "error", // ( message [,level] ) -> ERR
	static final class error extends TwoArgFunction {
		@Override
        public LuaValue call(LuaValue arg1, LuaValue arg2) {
			throw new LuaError( arg1.isnil()? null: arg1.tojstring(), arg2.optint(1) );
		}
	}

	// "getmetatable", // ( object ) -> table 
	static final class getmetatable extends LibFunction {
		@Override
        public LuaValue call() {
			return argerror(1, "value");
		}
		@Override
        public LuaValue call(LuaValue arg) {
			LuaValue mt = arg.getmetatable();
			return mt!=null? mt.rawget(METATABLE).optvalue(mt): NIL;
		}
	}

	// "pcall", // (f, arg1, ...) -> status, result1, ...
	final class pcall extends VarArgFunction {
		@Override
        public Varargs invoke(Varargs args) {
			LuaValue func = args.checkvalue(1);
			if (globals != null && globals.debuglib != null)
				globals.debuglib.onCall(this);
			try {
				return varargsOf(TRUE, func.invoke(args.subargs(2)));
			} catch ( LuaError le ) {
				final String m = le.getMessage();
				return varargsOf(FALSE, m!=null? valueOf(m): NIL);
			} catch ( Exception e ) {
				final String m = e.getMessage();
				return varargsOf(FALSE, valueOf(m!=null? m: e.toString()));
			} finally {
				if (globals != null && globals.debuglib != null)
					globals.debuglib.onReturn();
			}
		}
	}

	// "print", // (...) -> void
	final class print extends VarArgFunction {
		final BaseLib baselib;
		print(BaseLib baselib) {
			this.baselib = baselib;
		}
		@Override
        public Varargs invoke(Varargs args) {
			LuaValue tostring = globals.get("tostring"); 
			for ( int i=1, n=args.narg(); i<=n; i++ ) {
				if ( i>1 ) globals.STDOUT.print( '\t' );
				LuaString s = tostring.call( args.arg(i) ).strvalue();
				globals.STDOUT.print(s.tojstring());
			}
			globals.STDOUT.println();
			return NONE;
		}
	}
	

	// "rawequal", // (v1, v2) -> boolean
	static final class rawequal extends LibFunction {
		@Override
        public LuaValue call() {
			return argerror(1, "value");
		}
		@Override
        public LuaValue call(LuaValue arg) {
			return argerror(2, "value");
		}
		@Override
        public LuaValue call(LuaValue arg1, LuaValue arg2) {
			return valueOf(arg1.raweq(arg2));
		}
	}

	// "rawget", // (table, index) -> value
	static final class rawget extends LibFunction {
		@Override
        public LuaValue call() {
			return argerror(1, "value");
		}
		@Override
        public LuaValue call(LuaValue arg) {
			return argerror(2, "value");
		}
		@Override
        public LuaValue call(LuaValue arg1, LuaValue arg2) {
			return arg1.checktable().rawget(arg2);
		}
	}

	
	// "rawlen", // (v) -> value
	static final class rawlen extends LibFunction {
		@Override
        public LuaValue call(LuaValue arg) {
			return valueOf(arg.rawlen());
		}
	}

	// "rawset", // (table, index, value) -> table
	static final class rawset extends LibFunction {
		@Override
        public LuaValue call(LuaValue table) {
			return argerror(2,"value");
		}
		@Override
        public LuaValue call(LuaValue table, LuaValue index) {
			return argerror(3,"value");
		}
		@Override
        public LuaValue call(LuaValue table, LuaValue index, LuaValue value) {
			LuaTable t = table.checktable();
			t.rawset(index.checknotnil(), value);
			return t;
		}
	}
	
	// "select", // (f, ...) -> value1, ...
	static final class select extends VarArgFunction {
		@Override
        public Varargs invoke(Varargs args) {
			int n = args.narg()-1; 				
			if ( args.arg1().equals(valueOf("#")) )
				return valueOf(n);
			int i = args.checkint(1);
			if ( i == 0 || i < -n )
				argerror(1,"index out of range");
			return args.subargs(i<0? n+i+2: i+1);
		}
	}
	
	// "setmetatable", // (table, metatable) -> table
	static final class setmetatable extends LibFunction {
		@Override
        public LuaValue call(LuaValue table) {
			return argerror(2,"value");
		}
		@Override
        public LuaValue call(LuaValue table, LuaValue metatable) {
			final LuaValue mt0 = table.getmetatable();
			if ( mt0!=null && !mt0.rawget(METATABLE).isnil() )
				error("cannot change a protected metatable");
			return table.setmetatable(metatable.isnil()? null: metatable.checktable());
		}
	}
	
	// "tonumber", // (e [,base]) -> value
	static final class tonumber extends LibFunction {
		@Override
        public LuaValue call(LuaValue e) {
			return e.tonumber();
		}
		@Override
        public LuaValue call(LuaValue e, LuaValue base) {
			if (base.isnil())
				return e.tonumber();
			final int b = base.checkint();
			if ( b < 2 || b > 36 )
				argerror(2, "base out of range");
			return e.checkstring().tonumber(b);
		}
	}
	
	// "tostring", // (e) -> value
	static final class tostring extends LibFunction {
		@Override
        public LuaValue call(LuaValue arg) {
			LuaValue h = arg.metatag(TOSTRING);
			if ( ! h.isnil() ) 
				return h.call(arg);
			LuaValue v = arg.tostring();
			if ( ! v.isnil() ) 
				return v;
			return valueOf(arg.tojstring());
		}
	}

	// "type",  // (v) -> value
	static final class type extends LibFunction {
		@Override
        public LuaValue call(LuaValue arg) {
			return valueOf(arg.typename());
		}
	}

	// "xpcall", // (f, err) -> result1, ...				
	final class xpcall extends VarArgFunction {
		@Override
        public Varargs invoke(Varargs args) {
			final LuaThread t = globals.running;
			final LuaValue preverror = t.errorfunc;
			t.errorfunc = args.checkvalue(2);
			try {
				if (globals != null && globals.debuglib != null)
					globals.debuglib.onCall(this);
				try {
					return varargsOf(TRUE, args.arg1().invoke(args.subargs(3)));
				} catch ( LuaError le ) {
					final String m = le.getMessage();
					return varargsOf(FALSE, m!=null? valueOf(m): NIL);
				} catch ( Exception e ) {
					final String m = e.getMessage();
					return varargsOf(FALSE, valueOf(m!=null? m: e.toString()));
				} finally {
					if (globals != null && globals.debuglib != null)
						globals.debuglib.onReturn();
				}
			} finally {
				t.errorfunc = preverror;
			}
		}
	}
	
	// "pairs" (t) -> iter-func, t, nil
	static final class pairs extends VarArgFunction {
		final next next;
		pairs(next next) {
			this.next = next;
		}
		@Override
        public Varargs invoke(Varargs args) {
				return varargsOf( next, args.checktable(1), NIL );
		}
	}
	
	// // "ipairs", // (t) -> iter-func, t, 0
	static final class ipairs extends VarArgFunction {
		inext inext = new inext();
		@Override
        public Varargs invoke(Varargs args) {
			return varargsOf( inext, args.checktable(1), ZERO );
		}
	}
	
	// "next"  ( table, [index] ) -> next-index, next-value
	static final class next extends VarArgFunction {
		@Override
        public Varargs invoke(Varargs args) {
			return args.checktable(1).next(args.arg(2));
		}
	}
	
	// "inext" ( table, [int-index] ) -> next-index, next-value
	static final class inext extends VarArgFunction {
		@Override
        public Varargs invoke(Varargs args) {
			return args.checktable(1).inext(args.arg(2));
		}
	}
}