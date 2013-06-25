package com.personalpiano;

import android.content.Context;

public class Util {
	public static class BitVector{
		private int[] datamap;
		public BitVector(int length){
			datamap = new int [1 + length/32];
		}
		public void set(int i){
			datamap[i>>5] |= (1<<(i & 0x1F));
		}
		public void clear(int i){
			datamap[i>>5] &= ~(1<<(i & 0x1F));
		}
		public boolean test(int i){
			return (datamap[i>>5] & (1<<(i & 0x1F))) != 0;
		}
	}
	public static String getStringFromResource(Context ctx, int id){
		return ctx.getResources().getString(id);
	}
	
}
